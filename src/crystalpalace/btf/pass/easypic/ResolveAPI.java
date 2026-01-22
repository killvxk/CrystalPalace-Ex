package crystalpalace.btf.pass.easypic;

import crystalpalace.btf.*;
import crystalpalace.btf.Code;
import crystalpalace.btf.pass.*;
import crystalpalace.coff.*;
import crystalpalace.util.*;
import crystalpalace.export.*;

import java.util.*;
import java.io.*;

import com.github.icedland.iced.x86.*;
import com.github.icedland.iced.x86.asm.*;
import com.github.icedland.iced.x86.enc.*;
import com.github.icedland.iced.x86.dec.*;
import com.github.icedland.iced.x86.fmt.*;
import com.github.icedland.iced.x86.fmt.gas.*;
import com.github.icedland.iced.x86.info.*;

/* This is kind of a cool set of transforms, we're looking for instructions that have a relocation for
 * MODULE$Function and we're rewriting the program to call a DFR function (in the program) to resolve
 * that relocation using the ROR-hash of the module and function as args. This makes it a lot easier to
 * write PIC, because calling Win32 APIs is now seamless and arguably, framework-less. */
public class ResolveAPI extends BaseModify {
	protected DFR          resolvers = null;
	protected ParseImport  resolveme = null;
	protected DFR.Resolver resolver  = null;

	public void setupVerbs() {
		if (x64) {
			verbs.add(new MovRax());
			verbs.add(new MovNotRax());
			verbs.add(new Call64());
			verbs.add(new Jmp64());
		}
		else {
			verbs.add(new MovEax());
			verbs.add(new MovNotEax());
			verbs.add(new Call32());
			verbs.add(new Jmp32());
		}
	}

	public boolean shouldModify(RebuildStep step, Instruction next) {
		/* we're only interested, in instructions associated with relocations */
		if (!step.hasRelocation())
			return false;

		/* parse our relocation and check for a valid import function string */
		resolveme = new ParseImport(step.getRelocation().getSymbolName());

		if (!resolveme.isValid())
			return false;

		/* check that our import is in MODULE$Function format or its GetProcAddress/LoadLibraryA */
		resolveme.checkAndPopulateModule();

		/* find the appropriate DFR resolver for this import. Note, it will throw an exception if no resolver matches */
		resolver = resolvers.getResolver(resolveme);

		return true;
	}

	/* fail if we can't resolve something */
	public void noMatch(CodeAssembler program, RebuildStep step, Instruction next) {
		System.out.println(next.getOpCode().toInstructionString());
		CodeUtils.printInst(code, next);
		CodeInfo.Dump(next, null);

		throw new RuntimeException("Can't transform '" + step.getInstructionString() + "' to resolve API " + step.getRelocation() + ". Change your compiler optimization settings or turn off optimizations for the caller function.");
	}

	public ResolveAPI(Code code, DFR resolvers) {
		super(code);
		this.resolvers = resolvers;
	}

	public int pushString(CodeAssembler program, String text) {
		/* generate a zero-terminated byte array with our string */
		Concat temp = new Concat();
		temp.add(CrystalUtils.toBytes(text, "UTF-8"));
		temp.add(new byte[1]); // null terminator

		/* align it to the right size for our arch */
		temp.align(x64 ? 16 : 8);

		/* create a ByteWalker */
		ByteWalker walker = new ByteWalker(CrystalUtils.reverse(temp.get()));
		walker.big();

		int stacklen = 0;

		if (x64) {
			AsmRegister64 rax = new AsmRegister64(ICRegisters.rax);
			AsmRegister64 rsp = new AsmRegister64(ICRegisters.rsp);

			while (!walker.isComplete()) {
				long val = walker.readLong();

				if (val == 0) {
					program.xor(rax, rax);
					program.push(rax);
				}
				else {
					program.mov(rax, val);
					program.push(rax);
				}
				stacklen += 8;
			}
		}
		else {
			while (!walker.isComplete()) {
				program.push(walker.readInt());
				stacklen += 4;
			}
		}

		return stacklen;
	}

	public void resolve_strings_x86(CodeAssembler program, RebuildStep step, Instruction next) {
		AsmRegister32     esp = new AsmRegister32(ICRegisters.esp);
		AsmRegister32     ecx = new AsmRegister32(ICRegisters.ecx);
		AsmRegister32     edx = new AsmRegister32(ICRegisters.edx);

		/* save registers */
		List saved = pushad(program);

		/* call our resolver function */
		int total = 0;

		total += pushString(program, resolveme.getModule());
		program.mov(ecx, esp);

		total += pushString(program, resolveme.getFunction());
		program.mov(edx, esp);

		program.push(edx);
		program.push(ecx);

		program.call(step.getLabel(resolver.getFunction()));

		/* clean up the stack! */
		program.add(esp, 0x8 + total);

		/* restore registers */
		popad(program, saved);

		/* mark this instruction-associated relocation as resolved */
		step.resolve();
	}

	public void resolve_ror_x86(CodeAssembler program, RebuildStep step, Instruction next) {
		AsmRegister32 esp = new AsmRegister32(ICRegisters.esp);
		AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);

		/* save registers */
		List saved = pushad(program);

		/* call our resolver function */
		program.push(resolveme.getFunctionHash());
		program.push(resolveme.getModuleHash());

		program.call(step.getLabel(resolver.getFunction()));

		/* clean up the stack! */
		program.add(esp, 0x8);

		/* restore registers */
		popad(program, saved);

		/* mark this instruction-associated relocation as resolved */
		step.resolve();
	}

	public void resolve_x86(CodeAssembler program, RebuildStep step, Instruction next) {
		if (resolver.isRor13())
			resolve_ror_x86(program, step, next);
		else if (resolver.isStrings())
			resolve_strings_x86(program, step, next);
		else
			throw new RuntimeException("Invalid resolve method: " + resolver);
	}

	public void resolve_strings_x64(CodeAssembler program, RebuildStep step, Instruction next) {
		AsmRegister64 rcx = new AsmRegister64(ICRegisters.rcx);
		AsmRegister64 rdx = new AsmRegister64(ICRegisters.rdx);
		AsmRegister64 rsp = new AsmRegister64(ICRegisters.rsp);

		/* save registers */
		List saved = pushad(program);

		/* push our strings and store their pointers in ecx and edx */
		int total = 0;

		total += pushString(program, resolveme.getModule());
		program.mov(rcx, rsp);

		total += pushString(program, resolveme.getFunction());
		program.mov(rdx, rsp);

		/* alloc our shadowspace */
		stackAlloc(program, step.isDirty() ? 0x28 : 0x20);
				//  ^-- check if current function is %rsp aligned or not

		/* call our resolver function */
		program.call(step.getLabel(resolver.getFunction()));

		/* dealloc our shadowspace + the stack strings we just pushed too */
		stackDealloc(program, step.isDirty() ? (0x28 + total) : (0x20 + total));

		/* restore registers */
		popad(program, saved);

		/* mark this instruction-associated relocation as resolved */
		step.resolve();
	}

	public void resolve_ror_x64(CodeAssembler program, RebuildStep step, Instruction next) {
		AsmRegister32 ecx = new AsmRegister32(ICRegisters.ecx);
		AsmRegister32 edx = new AsmRegister32(ICRegisters.edx);

		/* save registers */
		List saved = pushad(program);

		/* create our shadowspace for x64 ABI */
		stackAlloc(program, step.isDirty() ? 0x28 : 0x20);
				//  ^-- check if current function is %rsp aligned or not

		/* call our resolver function */
		program.mov(ecx, resolveme.getModuleHash());
		program.mov(edx, resolveme.getFunctionHash());

		program.call(step.getLabel(resolver.getFunction()));

		/* get rid of our x64 ABI shadowspace */
		stackDealloc(program, step.isDirty() ? 0x28 : 0x20);

		/* restore registers */
		popad(program, saved);

		/* mark this instruction-associated relocation as resolved */
		step.resolve();
	}

	public void resolve_x64(CodeAssembler program, RebuildStep step, Instruction next) {
		if (resolver.isRor13())
			resolve_ror_x64(program, step, next);
		else if (resolver.isStrings())
			resolve_strings_x64(program, step, next);
		else
			throw new RuntimeException("Invalid resolve method: " + resolver);
	}

	private class Call64 implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "CALL r/m64".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			resolve_x64(program, step, next);

			AsmRegister64 rax = new AsmRegister64(ICRegisters.rax);
			program.call(rax);
		}
		// 10/26/25 - test 13 (likely due to optimizations)
	}

	private class Call32 implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "CALL r/m32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			resolve_x86(program, step, next);

			AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);
			program.call(eax);
		}
		// 10/26/25 - test 13 (likely due to optimizations)
	}

	private class Jmp64 implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "JMP r/m64".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			resolve_x64(program, step, next);

			AsmRegister64 rax = new AsmRegister64(ICRegisters.rax);
			program.jmp(rax);
		}
		// 01/09/26 - test 47 and 48
	}

	private class Jmp32 implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "JMP r/m32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			resolve_x86(program, step, next);

			AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);
			program.jmp(eax);
		}
		// 01.09/26 - test 48
	}

	private class MovEax implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV EAX, moffs32".equals(istr);
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			resolve_x86(program, step, next);
		}
		// 10/26/25 - very well represented in unit tests.
	}

	private class MovNotEax implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV r32, r/m32".equals(istr) && next.getOp0Register() != Register.EAX;
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister32 eax = new AsmRegister32(ICRegisters.eax);
			AsmRegister32 dst = new AsmRegister32( new ICRegister(next.getOp0Register()) );

			program.push(eax);
			resolve_x86(program, step, next);
			program.mov(dst, eax);
			program.pop(eax);
		}
		// 10/26/25 - represented in unit test 26
	}


	private class MovRax implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV r64, r/m64".equals(istr) && next.getOp0Register() == Register.RAX;
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			resolve_x64(program, step, next);
		}
		// 10/26/25 - very well represented in unit tests.
	}

	private class MovNotRax implements ModifyVerb {
		public boolean check(String istr, Instruction next) {
			return "MOV r64, r/m64".equals(istr) && next.getOp0Register() != Register.RAX;
		}

		public void apply(CodeAssembler program, RebuildStep step, Instruction next) {
			AsmRegister64 rax = new AsmRegister64(ICRegisters.rax);
			AsmRegister64 dst = new AsmRegister64( new ICRegister(next.getOp0Register()) );

			pushrax(program);
			resolve_x64(program, step, next);
			program.mov(dst, rax);
			poprax(program);
		}
		// 2026.01.06 - well represented in unit tests.
	}
}
