package com.donglouqiaoque.dlqqmcpserver.mcp;

/**
 * 控制台选项卡刷新接口：点击菜单栏全局「刷新」按钮时，由当前选中的选项卡实现自己的刷新逻辑。
 */
public interface RefreshableTab {

    /** 刷新当前选项卡内容（实现方应在后台线程执行耗时操作，避免阻塞 EDT）。 */
    void refresh();
}
