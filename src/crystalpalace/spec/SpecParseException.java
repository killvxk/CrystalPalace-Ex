package crystalpalace.spec;

import java.util.*;

/**
 * Something went wrong while parsing a .spec file.
 *
 * @author Raphael Mudge
 */
public class SpecParseException extends Exception {
	/**
	 * A collection of errors we found
	 */
	protected LinkedList errors  = new LinkedList();

	/**
	 * The .spec file where these errors came from
	 */
	protected String     parent  = "";

	/**
	 * Construct a new {@code SpecParseException}
	 *
	 * @param p the parser that detected the error
	 * @param _parent the file where the error probably occurred
	 */
	public SpecParseException(SpecParser p, String _parent) {
		errors.addAll(p.getErrors());
		parent = _parent;
	}

	/**
	 * Get the parsing errors that were discovered while parsing this file. Sometimes,
	 * there's more than one. Each entry in this {@code java.util.List} is a {@code java.lang.String}
	 *
	 * @return a list of errors
	 */
	public List getErrors() {
		return errors;
	}

	/**
	 * Returns a generic error message string, something like {@code Error(s) parsing [file here]}. So exciting.
	 *
	 * @return a message associated with this exception
	 */
	public String getMessage() {
		return "Error(s) parsing " + parent;
	}

	/**
	 * Get the file associated with this exception
	 *
	 * @return the parent file value
	 */
	public String getFile() {
		return parent;
	}

	/**
	 * Convert this exception to a human readable string. It's a multi-line string. If I were using this API,
	 * this is the method I would call. Just display this information to the user and they'll figure it out.
	 *
	 * @return the parser exception formatted nicely-ish.
	 */
	public String toString() {
		StringBuffer temp = new StringBuffer();
		temp.append(getMessage());

		Iterator i = errors.iterator();
		while (i.hasNext()) {
			String next = (String)i.next();
			temp.append("\n* " + next);
		}

		return temp.toString();
	}
}
