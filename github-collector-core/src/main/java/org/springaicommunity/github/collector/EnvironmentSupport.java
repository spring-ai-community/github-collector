package org.springaicommunity.github.collector;

import io.github.cdimascio.dotenv.Dotenv;
import org.jspecify.annotations.Nullable;

/**
 * Resolves environment variables by checking a {@code .env} file first, then falling back
 * to the system environment. The {@code .env} file is loaded once and cached for the
 * lifetime of the process.
 *
 * <p>
 * Lookup order:
 * <ol>
 * <li>{@code .env} file in the current working directory (if present)</li>
 * <li>System environment variable ({@link System#getenv})</li>
 * <li>{@code .env} file in the user's home directory (if present)</li>
 * </ol>
 */
public final class EnvironmentSupport {

	private static final Dotenv CWD_DOTENV = Dotenv.configure().ignoreIfMissing().ignoreIfMalformed().load();

	private static final Dotenv HOME_DOTENV = loadHomeDotenv();

	private static Dotenv loadHomeDotenv() {
		String home = System.getProperty("user.home");
		if (home != null) {
			return Dotenv.configure().directory(home).ignoreIfMissing().ignoreIfMalformed().load();
		}
		return CWD_DOTENV;
	}

	private EnvironmentSupport() {
	}

	/**
	 * Get an environment variable value.
	 * @param name the variable name
	 * @return the value, or {@code null} if not found
	 */
	@Nullable
	public static String get(String name) {
		String value = CWD_DOTENV.get(name);
		if (value == null) {
			value = HOME_DOTENV.get(name);
		}
		return value;
	}

}
