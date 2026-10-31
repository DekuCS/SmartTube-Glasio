package io.github.ivanosh.stmaterial.data

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySourceTest {
    private fun group(items: Int): MediaGroup {
        val item = fake<MediaItem> { null }
        return fake<MediaGroup> { name -> if (name == "getMediaItems") List(items) { item } else null }
    }

    private inline fun <reified T> fake(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args[0]
                "toString" -> "fake"
                else -> answer(method.name)
            }
        } as T

    @Test
    fun takesOnlyTheFirstPage() {
        val got = mutableListOf<MediaGroup>()
        val first = group(2)
        HistorySource { Observable.just(first, group(3)) }.load { got.add(it) }
        assertEquals(listOf(first), got)
    }

    @Test
    fun anEmptyHistoryIsNotAnAnswer() {
        val got = mutableListOf<MediaGroup>()
        HistorySource { Observable.just(group(0)) }.load { got.add(it) }
        assertTrue(got.isEmpty())
    }

    @Test
    fun aFailedRequestIsNotAnError() {
        val got = mutableListOf<MediaGroup>()
        HistorySource { Observable.error(RuntimeException("offline")) }.load { got.add(it) }
        assertTrue(got.isEmpty())
    }

    @Test
    fun disposeStopsTheRequestAndSilencesItsAnswer() {
        val subject = PublishSubject.create<MediaGroup>()
        val got = mutableListOf<MediaGroup>()
        val source = HistorySource { subject }
        source.load { got.add(it) }
        assertTrue(subject.hasObservers())
        source.dispose()
        assertFalse(subject.hasObservers())
        subject.onNext(group(1))
        assertTrue(got.isEmpty())
    }

    @Test
    fun aNewLoadReplacesTheRunningOne() {
        val first = PublishSubject.create<MediaGroup>()
        val second = PublishSubject.create<MediaGroup>()
        val subjects = ArrayDeque(listOf(first, second))
        val got = mutableListOf<MediaGroup>()
        val source = HistorySource { subjects.removeFirst() }
        source.load { got.add(it) }
        source.load { got.add(it) }
        assertFalse(first.hasObservers())
        second.onNext(group(1))
        assertEquals(1, got.size)
    }
}
