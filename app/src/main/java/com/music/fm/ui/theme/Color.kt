package com.music.fm.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Lumio（蓝田生玉）主题色板：
 * 主色 rgb(52,152,219) 蓝，白底 + 浅灰细分割线，辅以 badge 绿。
 */
val LumioPrimary = Color(0xFF3498DB)
val LumioPrimaryDark100 = Color(0xFF2F89C5)
val LumioPrimaryLight900 = Color(0xFFE4F1FA)
val LumioPrimaryLight600 = Color(0xFFCAE4F6)
val LumioGreen = Color(0xFF5CBF9B)

/** 文本：主文本 #333，次级 #999，占位 #B0B0B0 */
val LumioFont = Color(0xFF333333)
val LumioFontLabel = Color(0xFF909090)
val LumioFontPlaceholder = Color(0xFFB0B0B0)

/** 背景：内容白，页面浅蓝灰，分割线 #EFEFEF / 边框 #E6E6E6 */
val LumioContentBackground = Color(0xFFFFFFFF)
val LumioAppBackground = Color(0xFFF6FAFD)
val LumioBorder = Color(0xFFE6E6E6)
val LumioDivider = Color(0xFFEFEFEF)
val LumioKeyBackground = Color(0xFFF5F5F5)

/** 榜单方块底色（对齐 Lumio TV 硬编码的浅紫） */
val LumioBoardCard = Color(0xFFF5F3FF)

/**
 * TV 遥控器焦点框：主色蓝描边 + 15% 主色底，内侧再压一圈细白线与内容分隔。
 */
val FocusFill = Color(0x263498DB) // rgba(52,152,219,0.15)
val FocusRing = LumioPrimary
val FocusWhite = Color(0xFFFFFFFF)
