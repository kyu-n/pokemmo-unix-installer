package com.pokeemu.unix.ui;

/**
 * Interface for progress reporting that can be implemented by both UI and headless components
 */
public interface IProgressReporter {
	/**
	 * Set status message and progress
	 * @param message Status message to display
	 * @param progressValue Progress value (0-100)
	 */
	void setStatus(String message, int progressValue);

	/**
	 * Add detail message with optional progress update
	 * @param messageKey Message key for localization
	 * @param progressValue Progress value (0-100), use -1 to skip progress update
	 * @param params Parameters for message formatting
	 */
	void addDetail(String messageKey, int progressValue, Object... params);

	/**
	 * Show information message
	 * @param messageKey Message key for localization
	 * @param params Parameters for message formatting
	 */
	void showInfo(String messageKey, Object... params);

	/**
	 * Show error message
	 * @param message Error message
	 * @param title Error title
	 * @param onClose Callback to run when error is acknowledged (may be null)
	 */
	void showError(String message, String title, Runnable onClose);

	/**
	 * Set download speed for display
	 * @param speed Download speed string
	 */
	void setDownloadSpeed(String speed);
}