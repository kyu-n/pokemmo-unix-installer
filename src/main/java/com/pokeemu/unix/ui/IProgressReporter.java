package com.pokeemu.unix.ui;

public interface IProgressReporter
{
	void setStatus(String message, int progressValue);

	void addDetail(String messageKey, int progressValue, Object... params);

	void showInfo(String messageKey, Object... params);

	void showError(String message, String title, Runnable onClose);

	void setDownloadSpeed(String speed);
}