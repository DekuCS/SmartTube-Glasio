package io.github.ivanosh.stmaterial.data

import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService

/** The slice of SidebarService that section mirroring needs, so the logic can run on a plain JVM. */
interface SidebarPort {
    fun index(id: Int): Int
    fun canMoveUp(id: Int): Boolean
    fun moveUp(id: Int)
    fun enable(id: Int, on: Boolean)
    fun setBoot(id: Int)
}

class SidebarServicePort(private val sidebar: SidebarService) : SidebarPort {
    override fun index(id: Int) = sidebar.getSectionIndex(id)
    override fun canMoveUp(id: Int) = sidebar.canMoveSectionUp(id)
    override fun moveUp(id: Int) = sidebar.moveSectionUp(id)
    override fun enable(id: Int, on: Boolean) = sidebar.enableSection(id, on)
    override fun setBoot(id: Int) = sidebar.setBootSectionId(id)
}
