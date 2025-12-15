package crystalpalace.btf;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;

import com.github.icedland.iced.x86.*;
import com.github.icedland.iced.x86.dec.*;
import com.github.icedland.iced.x86.info.*;

/*
 * This is just this example from iced, helpful for understanding what's going on with an instruction.
 *
 * https://github.com/icedland/iced/blob/master/src/java/iced-x86/README.md#get-instruction-info-eg-readwritten-regsmem-control-flow-info-etc
 */
public class CodeInfo {
	public static void Dump(Instruction instr, ConstantOffsets offsets) {
		//System.out.println(String.format("%016X %s", instr.getIP(), instr));

		InstructionInfoFactory instrInfoFactory = new InstructionInfoFactory();

		OpCodeInfo opCode = instr.getOpCode();
		InstructionInfo info = instrInfoFactory.getInfo(instr);
		FpuStackIncrementInfo fpuInfo = instr.getFpuStackIncrementInfo();
		System.out.println(String.format("    OpCode: %s", opCode.toOpCodeString()));
		System.out.println(String.format("    Instruction: %s", opCode.toInstructionString()));
		System.out.println(String.format("    Encoding: %s", toEncoding(instr.getEncoding())));
		System.out.println(String.format("    Mnemonic: %s", toMnemonic(instr.getMnemonic())));
		System.out.println(String.format("    Code: %s", toCode(instr.getCode())));
		System.out.println(String.format("    CpuidFeature: %s", toCpuidFeatures(instr.getCpuidFeatures())));
		System.out.println(String.format("    FlowControl: %s", toFlowControl(instr.getFlowControl())));

		if (fpuInfo.writesTop) {
			if (fpuInfo.increment == 0)
				System.out.println("    FPU TOP: the instruction overwrites TOP");
			else
				System.out.println(String.format("    FPU TOP inc: %d", fpuInfo.increment));
			System.out.println(String.format("    FPU TOP cond write: %s", fpuInfo.conditional ? "true" : "false"));
		}

		if (offsets != null) {
			if (offsets.hasDisplacement())
				System.out.println(String.format("    Displacement offset = %d, size = %d", offsets.displacementOffset, offsets.displacementSize));
			if (offsets.hasImmediate())
				System.out.println(String.format("    Immediate offset = %d, size = %d", offsets.immediateOffset, offsets.immediateSize));
			if (offsets.hasImmediate2())
				System.out.println(String.format("    Immediate #2 offset = %d, size = %d", offsets.immediateOffset2, offsets.immediateSize2));
		}

		if (instr.isStackInstruction())
			System.out.println(String.format("    SP Increment: %d", instr.getStackPointerIncrement()));
		if (instr.getConditionCode() != ConditionCode.NONE)
			System.out.println(String.format("    Condition code: %s", toConditionCode(instr.getConditionCode())));
		if (instr.getRflagsRead() != RflagsBits.NONE)
			System.out.println(String.format("    RFLAGS Read: %s", toRflagsBits(instr.getRflagsRead())));
		if (instr.getRflagsWritten() != RflagsBits.NONE)
			System.out.println(String.format("    RFLAGS Written: %s", toRflagsBits(instr.getRflagsWritten())));
		if (instr.getRflagsCleared() != RflagsBits.NONE)
			System.out.println(String.format("    RFLAGS Cleared: %s", toRflagsBits(instr.getRflagsCleared())));
		if (instr.getRflagsSet() != RflagsBits.NONE)
			System.out.println(String.format("    RFLAGS Set: %s", toRflagsBits(instr.getRflagsSet())));
		if (instr.getRflagsUndefined() != RflagsBits.NONE)
			System.out.println(String.format("    RFLAGS Undefined: %s", toRflagsBits(instr.getRflagsUndefined())));
		if (instr.getRflagsModified() != RflagsBits.NONE)
			System.out.println(String.format("    RFLAGS Modified: %s", toRflagsBits(instr.getRflagsModified())));

		for (int i = 0; i < instr.getOpCount(); i++) {
			int opKind = instr.getOpKind(i);
			if (opKind == OpKind.MEMORY) {
				int size = MemorySize.getSize(instr.getMemorySize());
				if (size != 0)
					System.out.println(String.format("    Memory size: %d", size));
				break;
			}
		}

		for (int i = 0; i < instr.getOpCount(); i++)
			System.out.println(String.format("    Op%dAccess: %s", i, toOpAccess(info.getOpAccess(i))));

		for (int i = 0; i < opCode.getOpCount(); i++)
			System.out.println(String.format("    Op%d: %s", i, toOpCodeOperandKind(opCode.getOpKind(i))));

		for (UsedRegister regInfo : info.getUsedRegisters())
			System.out.println(String.format("    Used reg: %s", toUsedRegister(regInfo)));

		for (UsedMemory memInfo : info.getUsedMemory())
			System.out.println(String.format("    Used mem: %s", toUsedMemory(memInfo)));
	}

	static final HashMap<Integer, String> encodingMap;
	static final HashMap<Integer, String> mnemonicMap;
	static final HashMap<Integer, String> codeMap;
	static final HashMap<Integer, String> cpuidFeatureMap;
	static final HashMap<Integer, String> flowControlMap;
	static final HashMap<Integer, String> opAccessMap;
	static final HashMap<Integer, String> opCodeOperandKindMap;
	static final HashMap<Integer, String> conditionCodeMap;
	static final HashMap<Integer, String> registerMap;
	static final HashMap<Integer, String> memorySizeMap;

	static {
		encodingMap = createHashMap(EncodingKind.class);
		mnemonicMap = createHashMap(Mnemonic.class);
		codeMap = createHashMap(Code.class);
		cpuidFeatureMap = createHashMap(CpuidFeature.class);
		flowControlMap = createHashMap(FlowControl.class);
		opAccessMap = createHashMap(OpAccess.class);
		opCodeOperandKindMap = createHashMap(OpCodeOperandKind.class);
		conditionCodeMap = createHashMap(ConditionCode.class);
		registerMap = createHashMap(Register.class);
		memorySizeMap = createHashMap(MemorySize.class);
	}

	static HashMap<Integer, String> createHashMap(Class cls) {
		HashMap<Integer, String> result = new HashMap<Integer, String>();
		for (Field field : cls.getDeclaredFields()) {
			if ((field.getModifiers() & Modifier.FINAL) == 0)
				continue;
			if ((field.getModifiers() & Modifier.STATIC) == 0)
				continue;

			try {
				result.put(field.getInt(null), field.getName());
			}
			catch (IllegalAccessException ex) {
				throw new UnsupportedOperationException(ex);
			}
		}

		return result;
	}

	static String getMapValue(HashMap<Integer, String> map, int value) {
		String name = map.get(value);
		if (name != null)
			return name;
		return String.format("0x%X", value);
	}

	static String toEncoding(int value) { return getMapValue(encodingMap, value); }
	static String toMnemonic(int value) { return getMapValue(mnemonicMap, value); }
	static String toCode(int value) { return getMapValue(codeMap, value); }
	static String toCpuidFeature(int value) { return getMapValue(cpuidFeatureMap, value); }
	static String toFlowControl(int value) { return getMapValue(flowControlMap, value); }
	static String toConditionCode(int value) { return getMapValue(conditionCodeMap, value); }
	static String toOpAccess(int value) { return getMapValue(opAccessMap, value); }
	static String toOpCodeOperandKind(int value) { return getMapValue(opCodeOperandKindMap, value); }
	static String toRegister(int value) { return getMapValue(registerMap, value); }
	static String toMemorySize(int value) { return getMapValue(memorySizeMap, value); }

	static String toCpuidFeatures(int[] cpuidFeatures) {
		StringBuilder sb = new StringBuilder();
		for (int cpuidFeature : cpuidFeatures) {
			if (sb.length() > 0)
				sb.append(" and ");
			sb.append(toCpuidFeature(cpuidFeature));
		}

		return sb.toString();
	}

	static int addBit(StringBuilder sb, int value, int flag, String name) {
		if ((value & flag) != 0) {
			if (sb.length() != 0)
				sb.append(", ");
			sb.append(name);
			value &= ~flag;
		}

		return value;
	}

	static String toRflagsBits(int value) {
		StringBuilder sb = new StringBuilder();

		value = addBit(sb, value, RflagsBits.OF, "OF");
		value = addBit(sb, value, RflagsBits.SF, "SF");
		value = addBit(sb, value, RflagsBits.ZF, "ZF");
		value = addBit(sb, value, RflagsBits.AF, "AF");
		value = addBit(sb, value, RflagsBits.CF, "CF");
		value = addBit(sb, value, RflagsBits.PF, "PF");
		value = addBit(sb, value, RflagsBits.DF, "DF");
		value = addBit(sb, value, RflagsBits.IF, "IF");
		value = addBit(sb, value, RflagsBits.AC, "AC");
		value = addBit(sb, value, RflagsBits.UIF, "UIF");
		value = addBit(sb, value, RflagsBits.C0, "C0");
		value = addBit(sb, value, RflagsBits.C1, "C1");
		value = addBit(sb, value, RflagsBits.C2, "C2");
		value = addBit(sb, value, RflagsBits.C3, "C3");

		if (value != 0) {
			if (sb.length() != 0)
				sb.append(", ");
			sb.append(String.format("0x%X", value));
		}

		return sb.toString();
	}

	static String toUsedRegister(UsedRegister reg) {
		return toRegister(reg.getRegister()) + ":" + toOpAccess(reg.getAccess());
	}

	static String toUsedMemory(UsedMemory mem) {
		StringBuilder sb = new StringBuilder();
		sb.append('[');
		sb.append(toRegister(mem.getSegment()));
		sb.append(':');

		boolean needPlus = false;

		if (mem.getBase() != Register.NONE) {
			sb.append(toRegister(mem.getBase()));
			needPlus = true;
		}

		if (mem.getIndex() != Register.NONE) {
			if (needPlus)
				sb.append('+');
			needPlus = true;

			sb.append(toRegister(mem.getIndex()));
			if (mem.getScale() != 1) {
				sb.append('*');
				sb.append((char)('0' + mem.getScale()));
			}
		}

		if (mem.getDisplacement() != 0 || !needPlus) {
			if (needPlus)
				sb.append('+');
			sb.append("0x");
			sb.append(String.format("%X", mem.getDisplacement()));
		}

		sb.append(';');
		sb.append(toMemorySize(mem.getMemorySize()));
		sb.append(';');
		sb.append(toOpAccess(mem.getAccess()));
		sb.append(']');

		return sb.toString();
	}
}
