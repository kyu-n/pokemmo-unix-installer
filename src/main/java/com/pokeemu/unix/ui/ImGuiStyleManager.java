package com.pokeemu.unix.ui;

import com.pokeemu.unix.util.GnomeThemeDetector;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;

public class ImGuiStyleManager
{

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

	public static void applySystemTheme()
	{
		boolean darkMode = GnomeThemeDetector.isDark();
		if(darkMode)
		{
			applySpectrumDark();
		}
		else
		{
			applySpectrumLight();
		}
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

		// Define colors using hex codes directly
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

		// Apply colors
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
		setColor(style, ImGuiCol.TextSelectedBg, 0x33B6D3F9);  // 33 alpha + blue
		setColor(style, ImGuiCol.DragDropTarget, 1.0f, 1.0f, 0.0f, 0.9f);
		setColor(style, ImGuiCol.NavHighlight, 0x0A2C2C2C);  // 0A alpha + gray900
		setColor(style, ImGuiCol.NavWindowingHighlight, 1.0f, 1.0f, 1.0f, 0.7f);
		setColor(style, ImGuiCol.NavWindowingDimBg, 0.8f, 0.8f, 0.8f, 0.2f);
		setColor(style, ImGuiCol.ModalWindowDimBg, 0.2f, 0.2f, 0.2f, 0.35f);
	}

	public static void applySpectrumDark()
	{
		ImGuiStyle style = ImGui.getStyle();
		applyCommonStyle(style);

		// Define colors using hex codes directly
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

		// Apply colors
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
		setColor(style, ImGuiCol.TextSelectedBg, 0x33378EF0);  // 33 alpha + blue500
		setColor(style, ImGuiCol.DragDropTarget, 1.0f, 1.0f, 0.0f, 0.9f);
		setColor(style, ImGuiCol.NavHighlight, 0x0AFFFFFF);  // 0A alpha + gray900
		setColor(style, ImGuiCol.NavWindowingHighlight, 1.0f, 1.0f, 1.0f, 0.7f);
		setColor(style, ImGuiCol.NavWindowingDimBg, 0.8f, 0.8f, 0.8f, 0.2f);
		setColor(style, ImGuiCol.ModalWindowDimBg, 0.8f, 0.8f, 0.8f, 0.35f);
	}
}