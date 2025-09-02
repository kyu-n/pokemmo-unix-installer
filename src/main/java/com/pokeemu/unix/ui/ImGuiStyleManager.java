package com.pokeemu.unix.ui;

import com.pokeemu.unix.util.GnomeThemeDetector;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;

public class ImGuiStyleManager
{
	// Color constants for UI elements

	// Status colors
	public static float[] COLOR_ERROR = new float[4];
	public static float[] COLOR_WARNING = new float[4];
	public static float[] COLOR_SUCCESS = new float[4];
	public static float[] COLOR_INFO = new float[4];

	// Button state colors
	public static float[] COLOR_BUTTON_SUCCESS = new float[4];
	public static float[] COLOR_BUTTON_SUCCESS_HOVER = new float[4];
	public static float[] COLOR_BUTTON_SUCCESS_ACTIVE = new float[4];

	public static float[] COLOR_BUTTON_DANGER = new float[4];
	public static float[] COLOR_BUTTON_DANGER_HOVER = new float[4];
	public static float[] COLOR_BUTTON_DANGER_ACTIVE = new float[4];

	// Text colors
	public static float[] COLOR_TEXT_ERROR = new float[4];
	public static float[] COLOR_TEXT_WARNING = new float[4];
	public static float[] COLOR_TEXT_SUCCESS = new float[4];
	public static float[] COLOR_TEXT_INFO = new float[4];
	public static float[] COLOR_TEXT_MUTED = new float[4];

	// Special UI colors
	public static float[] COLOR_UPDATING_STATUS = new float[4];
	public static float[] COLOR_COPIED_FEEDBACK = new float[4];
	public static float[] COLOR_COPIED_FEEDBACK_HOVER = new float[4];

	private static void setColor(ImGuiStyle style, int colFlag, int argb)
	{
		float a = ((argb >> 24) & 0xFF) / 255.0f;
		float r = ((argb >> 16) & 0xFF) / 255.0f;
		float g = ((argb >> 8) & 0xFF) / 255.0f;
		float b = (argb & 0xFF) / 255.0f;
		style.setColor(colFlag, r, g, b, a);
	}

	private static void setColor(ImGuiStyle style, int colFlag, float r, float g, float b, float a)
	{
		style.setColor(colFlag, r, g, b, a);
	}

	private static void setColorArray(float[] target, float r, float g, float b, float a)
	{
		target[0] = r;
		target[1] = g;
		target[2] = b;
		target[3] = a;
	}

	public static void applySystemTheme()
	{
		boolean darkMode = GnomeThemeDetector.isDark();
		if(darkMode)
		{
			applySpectrumDark();
			applyDarkThemeColors();
		}
		else
		{
			applySpectrumLight();
			applyLightThemeColors();
		}
	}

	private static void applyLightThemeColors()
	{
		// Status colors - vibrant for light theme
		setColorArray(COLOR_ERROR, 0.9f, 0.2f, 0.2f, 1.0f);
		setColorArray(COLOR_WARNING, 0.9f, 0.7f, 0.0f, 1.0f);
		setColorArray(COLOR_SUCCESS, 0.2f, 0.7f, 0.2f, 1.0f);
		setColorArray(COLOR_INFO, 0.3f, 0.5f, 0.9f, 1.0f);

		// Button colors - Success (pastel mint green)
		setColorArray(COLOR_BUTTON_SUCCESS, 0.7f, 0.85f, 0.7f, 1.0f);
		setColorArray(COLOR_BUTTON_SUCCESS_HOVER, 0.65f, 0.82f, 0.65f, 1.0f);
		setColorArray(COLOR_BUTTON_SUCCESS_ACTIVE, 0.75f, 0.88f, 0.75f, 1.0f);

		// Button colors - Danger (pastel coral/pink)
		setColorArray(COLOR_BUTTON_DANGER, 0.9f, 0.7f, 0.7f, 1.0f);
		setColorArray(COLOR_BUTTON_DANGER_HOVER, 0.87f, 0.65f, 0.65f, 1.0f);
		setColorArray(COLOR_BUTTON_DANGER_ACTIVE, 0.93f, 0.75f, 0.75f, 1.0f);

		// Text colors
		setColorArray(COLOR_TEXT_ERROR, 1.0f, 0.2f, 0.2f, 1.0f);
		setColorArray(COLOR_TEXT_WARNING, 1.0f, 0.8f, 0.0f, 1.0f);
		setColorArray(COLOR_TEXT_SUCCESS, 0.0f, 0.7f, 0.0f, 1.0f);
		setColorArray(COLOR_TEXT_INFO, 0.4f, 0.6f, 1.0f, 1.0f);
		setColorArray(COLOR_TEXT_MUTED, 0.6f, 0.6f, 0.6f, 1.0f);

		// Special UI colors
		setColorArray(COLOR_UPDATING_STATUS, 1.0f, 0.8f, 0.0f, 1.0f);
		setColorArray(COLOR_COPIED_FEEDBACK, 0.0f, 0.6f, 0.0f, 1.0f);
		setColorArray(COLOR_COPIED_FEEDBACK_HOVER, 0.0f, 0.7f, 0.0f, 1.0f);
	}

	private static void applyDarkThemeColors()
	{
		// Status colors - slightly muted for dark theme
		setColorArray(COLOR_ERROR, 1.0f, 0.3f, 0.3f, 1.0f);
		setColorArray(COLOR_WARNING, 1.0f, 0.8f, 0.3f, 1.0f);
		setColorArray(COLOR_SUCCESS, 0.3f, 1.0f, 0.3f, 1.0f);
		setColorArray(COLOR_INFO, 0.7f, 0.7f, 1.0f, 1.0f);

		// Button colors - Success (dark muted green for better contrast with white text)
		setColorArray(COLOR_BUTTON_SUCCESS, 0.2f, 0.35f, 0.2f, 1.0f);
		setColorArray(COLOR_BUTTON_SUCCESS_HOVER, 0.25f, 0.4f, 0.25f, 1.0f);
		setColorArray(COLOR_BUTTON_SUCCESS_ACTIVE, 0.18f, 0.32f, 0.18f, 1.0f);

		// Button colors - Danger (dark muted red for better contrast with white text)
		setColorArray(COLOR_BUTTON_DANGER, 0.35f, 0.2f, 0.2f, 1.0f);
		setColorArray(COLOR_BUTTON_DANGER_HOVER, 0.4f, 0.25f, 0.25f, 1.0f);
		setColorArray(COLOR_BUTTON_DANGER_ACTIVE, 0.32f, 0.18f, 0.18f, 1.0f);

		// Text colors
		setColorArray(COLOR_TEXT_ERROR, 1.0f, 0.3f, 0.3f, 1.0f);
		setColorArray(COLOR_TEXT_WARNING, 1.0f, 0.8f, 0.0f, 1.0f);
		setColorArray(COLOR_TEXT_SUCCESS, 0.3f, 1.0f, 0.3f, 1.0f);
		setColorArray(COLOR_TEXT_INFO, 0.7f, 0.7f, 1.0f, 1.0f);
		setColorArray(COLOR_TEXT_MUTED, 0.7f, 0.7f, 0.7f, 1.0f);

		// Special UI colors
		setColorArray(COLOR_UPDATING_STATUS, 1.0f, 0.8f, 0.0f, 1.0f);
		setColorArray(COLOR_COPIED_FEEDBACK, 0.0f, 0.5f, 0.0f, 1.0f);
		setColorArray(COLOR_COPIED_FEEDBACK_HOVER, 0.0f, 0.6f, 0.0f, 1.0f);
	}

	private static void applyCommonStyle(ImGuiStyle style)
	{
		style.setAlpha(1.0f);
		style.setFrameRounding(4.0f);
		style.setGrabRounding(4.0f);
		style.setWindowRounding(6.0f);
		style.setChildRounding(4.0f);
		style.setPopupRounding(4.0f);
		style.setScrollbarRounding(4.0f);
		style.setTabRounding(4.0f);

		style.setWindowPadding(8.0f, 8.0f);
		style.setFramePadding(6.0f, 4.0f);
		style.setItemSpacing(8.0f, 6.0f);
		style.setItemInnerSpacing(6.0f, 4.0f);
		style.setIndentSpacing(20.0f);
		style.setScrollbarSize(12.0f);
		style.setGrabMinSize(10.0f);

		style.setWindowBorderSize(1.0f);
		style.setChildBorderSize(1.0f);
		style.setPopupBorderSize(1.0f);
		style.setFrameBorderSize(0.0f);
		style.setTabBorderSize(0.0f);
	}

	public static void applySpectrumLight()
	{
		ImGuiStyle style = ImGui.getStyle();
		applyCommonStyle(style);

		final int GRAY50 = 0xFFFFFFFF;
		final int GRAY75 = 0xFFFAFAFA;
		final int GRAY100 = 0xFFF5F5F5;
		final int GRAY200 = 0xFFEAEAEA;
		final int GRAY300 = 0xFFE1E1E1;
		final int GRAY400 = 0xFFCACACA;
		final int GRAY500 = 0xFFB3B3B3;
		final int GRAY600 = 0xFF8E8E8E;
		final int GRAY700 = 0xFF707070;
		final int GRAY800 = 0xFF4B4B4B;
		final int BLUE = 0xFFB6D3F9;

		setColor(style, ImGuiCol.Text, GRAY800);
		setColor(style, ImGuiCol.TextDisabled, GRAY500);
		setColor(style, ImGuiCol.WindowBg, GRAY100);
		setColor(style, ImGuiCol.ChildBg, 0.0f, 0.0f, 0.0f, 0.0f);
		setColor(style, ImGuiCol.PopupBg, GRAY50);
		setColor(style, ImGuiCol.Border, GRAY300);
		setColor(style, ImGuiCol.BorderShadow, 0.0f, 0.0f, 0.0f, 0.0f);
		setColor(style, ImGuiCol.FrameBg, GRAY75);
		setColor(style, ImGuiCol.FrameBgHovered, GRAY50);
		setColor(style, ImGuiCol.FrameBgActive, GRAY200);
		setColor(style, ImGuiCol.TitleBg, GRAY300);
		setColor(style, ImGuiCol.TitleBgActive, GRAY200);
		setColor(style, ImGuiCol.TitleBgCollapsed, GRAY400);
		setColor(style, ImGuiCol.MenuBarBg, GRAY100);
		setColor(style, ImGuiCol.ScrollbarBg, GRAY100);
		setColor(style, ImGuiCol.ScrollbarGrab, GRAY400);
		setColor(style, ImGuiCol.ScrollbarGrabHovered, GRAY600);
		setColor(style, ImGuiCol.ScrollbarGrabActive, GRAY700);
		setColor(style, ImGuiCol.CheckMark, BLUE);
		setColor(style, ImGuiCol.SliderGrab, GRAY700);
		setColor(style, ImGuiCol.SliderGrabActive, GRAY800);
		setColor(style, ImGuiCol.Button, GRAY200);
		setColor(style, ImGuiCol.ButtonHovered, BLUE);
		setColor(style, ImGuiCol.ButtonActive, GRAY400);
		setColor(style, ImGuiCol.Header, GRAY100);
		setColor(style, ImGuiCol.HeaderHovered, GRAY400);
		setColor(style, ImGuiCol.HeaderActive, GRAY200);
		setColor(style, ImGuiCol.Separator, GRAY400);
		setColor(style, ImGuiCol.SeparatorHovered, GRAY600);
		setColor(style, ImGuiCol.SeparatorActive, GRAY700);
		setColor(style, ImGuiCol.ResizeGrip, GRAY400);
		setColor(style, ImGuiCol.ResizeGripHovered, GRAY600);
		setColor(style, ImGuiCol.ResizeGripActive, GRAY700);
		setColor(style, ImGuiCol.Tab, GRAY200);
		setColor(style, ImGuiCol.TabHovered, GRAY300);
		setColor(style, ImGuiCol.TabActive, GRAY75);
		setColor(style, ImGuiCol.TabUnfocused, GRAY300);
		setColor(style, ImGuiCol.TabUnfocusedActive, GRAY100);
		setColor(style, ImGuiCol.PlotLines, BLUE);
		setColor(style, ImGuiCol.PlotLinesHovered, BLUE);
		setColor(style, ImGuiCol.PlotHistogram, BLUE);
		setColor(style, ImGuiCol.PlotHistogramHovered, BLUE);
		setColor(style, ImGuiCol.TextSelectedBg, 0x33B6D3F9);
		setColor(style, ImGuiCol.DragDropTarget, 1.0f, 1.0f, 0.0f, 0.9f);
		setColor(style, ImGuiCol.NavHighlight, 0x0A2C2C2C);
		setColor(style, ImGuiCol.NavWindowingHighlight, 1.0f, 1.0f, 1.0f, 0.7f);
		setColor(style, ImGuiCol.NavWindowingDimBg, 0.8f, 0.8f, 0.8f, 0.2f);
		setColor(style, ImGuiCol.ModalWindowDimBg, 0.2f, 0.2f, 0.2f, 0.35f);
	}

	public static void applySpectrumDark()
	{
		ImGuiStyle style = ImGui.getStyle();
		applyCommonStyle(style);

		final int GRAY50 = 0xFF252525;
		final int GRAY75 = 0xFF2F2F2F;
		final int GRAY100 = 0xFF323232;
		final int GRAY200 = 0xFF393939;
		final int GRAY300 = 0xFF3E3E3E;
		final int GRAY400 = 0xFF4D4D4D;
		final int GRAY500 = 0xFF5C5C5C;
		final int GRAY600 = 0xFF7B7B7B;
		final int GRAY700 = 0xFF999999;
		final int GRAY800 = 0xFFCDCDCD;
		final int BLUE500 = 0xFF378EF0;
		final int BLUE600 = 0xFF4B9CF5;

		setColor(style, ImGuiCol.Text, GRAY800);
		setColor(style, ImGuiCol.TextDisabled, GRAY500);
		setColor(style, ImGuiCol.WindowBg, GRAY100);
		setColor(style, ImGuiCol.ChildBg, 0.0f, 0.0f, 0.0f, 0.0f);
		setColor(style, ImGuiCol.PopupBg, GRAY50);
		setColor(style, ImGuiCol.Border, GRAY300);
		setColor(style, ImGuiCol.BorderShadow, 0.0f, 0.0f, 0.0f, 0.0f);
		setColor(style, ImGuiCol.FrameBg, GRAY75);
		setColor(style, ImGuiCol.FrameBgHovered, GRAY50);
		setColor(style, ImGuiCol.FrameBgActive, GRAY200);
		setColor(style, ImGuiCol.TitleBg, GRAY300);
		setColor(style, ImGuiCol.TitleBgActive, GRAY200);
		setColor(style, ImGuiCol.TitleBgCollapsed, GRAY400);
		setColor(style, ImGuiCol.MenuBarBg, GRAY100);
		setColor(style, ImGuiCol.ScrollbarBg, GRAY100);
		setColor(style, ImGuiCol.ScrollbarGrab, GRAY400);
		setColor(style, ImGuiCol.ScrollbarGrabHovered, GRAY600);
		setColor(style, ImGuiCol.ScrollbarGrabActive, GRAY700);
		setColor(style, ImGuiCol.CheckMark, BLUE500);
		setColor(style, ImGuiCol.SliderGrab, GRAY700);
		setColor(style, ImGuiCol.SliderGrabActive, GRAY800);
		setColor(style, ImGuiCol.Button, GRAY75);
		setColor(style, ImGuiCol.ButtonHovered, GRAY50);
		setColor(style, ImGuiCol.ButtonActive, GRAY200);
		setColor(style, ImGuiCol.Header, BLUE500);
		setColor(style, ImGuiCol.HeaderHovered, BLUE500);
		setColor(style, ImGuiCol.HeaderActive, BLUE600);
		setColor(style, ImGuiCol.Separator, GRAY400);
		setColor(style, ImGuiCol.SeparatorHovered, GRAY600);
		setColor(style, ImGuiCol.SeparatorActive, GRAY700);
		setColor(style, ImGuiCol.ResizeGrip, GRAY400);
		setColor(style, ImGuiCol.ResizeGripHovered, GRAY600);
		setColor(style, ImGuiCol.ResizeGripActive, GRAY700);
		setColor(style, ImGuiCol.Tab, GRAY200);
		setColor(style, ImGuiCol.TabHovered, GRAY300);
		setColor(style, ImGuiCol.TabActive, GRAY75);
		setColor(style, ImGuiCol.TabUnfocused, GRAY300);
		setColor(style, ImGuiCol.TabUnfocusedActive, GRAY100);
		setColor(style, ImGuiCol.PlotLines, BLUE500);
		setColor(style, ImGuiCol.PlotLinesHovered, BLUE600);
		setColor(style, ImGuiCol.PlotHistogram, BLUE500);
		setColor(style, ImGuiCol.PlotHistogramHovered, BLUE600);
		setColor(style, ImGuiCol.TextSelectedBg, 0x33378EF0);
		setColor(style, ImGuiCol.DragDropTarget, 1.0f, 1.0f, 0.0f, 0.9f);
		setColor(style, ImGuiCol.NavHighlight, 0x0AFFFFFF);
		setColor(style, ImGuiCol.NavWindowingHighlight, 1.0f, 1.0f, 1.0f, 0.7f);
		setColor(style, ImGuiCol.NavWindowingDimBg, 0.8f, 0.8f, 0.8f, 0.2f);
		setColor(style, ImGuiCol.ModalWindowDimBg, 0.8f, 0.8f, 0.8f, 0.35f);
	}
}