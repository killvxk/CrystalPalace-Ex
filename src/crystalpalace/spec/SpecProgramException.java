package crystalpalace.spec;

import java.util.*;

/**
 * Something went wrong while applying a specification file to some values
 *
 * @author Raphael Mudge
 */
public class SpecProgramException extends Exception {
	/**
	 * The program where the error occurred.
	 */
	protected SpecProgram program;

	/**
	 * The message associated with the error.
	 */
	protected String      message;

	/**
	 * Construct a new program exception.
	 *
	 * @param program The program where the error occurred. We need this to pull state information via these APIs.
	 * @param message A description of what went wrong. These are fairly specific.
	 */
	public SpecProgramException(SpecProgram program, String message) {
		this.program = program;
		this.message = message;
	}

	/**
	 * Returns the last command that was run or running when this error occured.
	 *
	 * @return the last command run
	 */
	public String getCommand() {
		return program.getLastCommand();
	}

	/**
	 * Returns the variables set in the last command that was or running when this error occured.
	 *
	 * @return the last command run
	 */
	public Map getVariables() {
		return program.getLastVars();
	}

	/**
	 * Returns the argument to the was-executing command. This is usually the item that was most recently popped off of the program stack.
	 *
	 * @return the argument when the last command was run
	 */
	public SpecObject getArgument() {
		return program.getLastArgument();
	}

	/**
	 * Get the stack from the .spec file's execution environment. This offers an additional clue about the program state when the error occured.
	 *
	 * @return the stack from the .spec file's execution environment
	 */
	public Stack getStack() {
		return program.getStack();
	}

	/**
	 * Get the description of the error that generated this exception. These are fairly descriptive and situation specific.
	 *
	 * @return the error message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Get the target that was executing within the .specification file (e.g., {@code x86, x64}).
	 *
	 * @return the target when the error occurred.
	 */
	public String getTarget() {
		return program.getLastTarget();
	}

	/**
	 * Get the file associated with this program. Note, in the case of the {@code run "file.spec"} command--it's possible that this file
	 * is not the parent file of the .spec. This will return the file where the error occured, not the top-level .spec file that was run.
	 *
	 * @return the path to the file where this error occurred.
	 */
	public String getFile() {
		return program.getFile();
	}

	/**
	 * Get the shortname of the file where this error occured. In the case of {@code run "file.spec"}--the file associated with this error
	 * may differ from the top-level .spec file that was run via {@link LinkSpec}
	 *
	 * @return the short file name associated where this error occured.
	 */
	public String getFileName() {
		return new java.io.File(program.getFile()).getName();
	}

	/**
	 * Generate a sane string representation of this error and the context where it occurred. If I were using this API, I would just call
	 * this method and present the results to the end-user. They'll figure out what it means.
	 *
	 * @return a string representation of this error
	 */
	public String toString() {
		StringBuffer temp = new StringBuffer();
		temp.append(getMessage() + " in " + getFileName() + " ("+getTarget()+")\n");

		temp.append("Last command:  " + getCommand() + "\n");

		if (getVariables().size() > 0)
			temp.append("Variables:     " + getVariables() + "\n");

		if (getArgument() != null)
			temp.append("Last argument: " + getArgument() + "\n");
		else
			temp.append("Last argument: [null]\n");
		temp.append("Stack:         " + getStack() + "\n");

		return temp.toString();
	}
}
