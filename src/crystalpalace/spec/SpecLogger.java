package crystalpalace.spec;

/**
 * An interface to receive output from Crystal Palace. Right now, this catches output of Crystal Palace's {@code echo}
 * command. It may do more in the future.
 *
 * Use {@link LinkSpec#addLogger} to register a logger for a Crystal Palace program.
 */
public interface SpecLogger {
	/**
	 * Act on output from Crystal Palace
	 *
	 * @param message the contents of the message.
	 */
	public void logSpecMessage(SpecMessage message);
}
