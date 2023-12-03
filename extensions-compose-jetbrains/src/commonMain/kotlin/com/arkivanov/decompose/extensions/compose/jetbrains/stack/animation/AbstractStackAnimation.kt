package com.arkivanov.decompose.extensions.compose.jetbrains.stack.animation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.arkivanov.decompose.Child
import com.arkivanov.decompose.router.stack.ChildStack

internal abstract class AbstractStackAnimation<C : Any, T : Any>(
    private val disableInputDuringAnimation: Boolean,
) : StackAnimation<C, T> {

    @Composable
    protected abstract fun Child(
        item: AnimationItem<C, T>,
        onFinished: () -> Unit,
        content: @Composable (child: Child.Created<C, T>) -> Unit,
    )

    @Composable
    override operator fun invoke(stack: ChildStack<C, T>, modifier: Modifier, content: @Composable (child: Child.Created<C, T>) -> Unit) {
        var currentStack by remember { mutableStateOf(stack) }
        var items by remember { mutableStateOf(getAnimationItems(newStack = currentStack, oldStack = null)) }

        if (stack.active.configuration != currentStack.active.configuration) {
            val oldStack = currentStack
            currentStack = stack
            items = getAnimationItems(newStack = currentStack, oldStack = oldStack)
        }

        Box(modifier = modifier) {
            items.forEach { (configuration, item) ->
                key(configuration) {
                    Child(
                        item = item,
                        onFinished = {
                            if (item.direction.isExit) {
                                items -= configuration
                            } else {
                                items += (configuration to item.copy(otherChild = null))
                            }
                        },
                        content = content,
                    )
                }
            }

            // A workaround until https://issuetracker.google.com/issues/214231672.
            // Normally only the exiting child should be disabled.
            if (disableInputDuringAnimation && (items.size > 1)) {
                Overlay(modifier = Modifier.matchParentSize())
            }
        }
    }

    @Composable
    private fun Overlay(modifier: Modifier) {
        Box(
            modifier = modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        )
    }

    private fun getAnimationItems(newStack: ChildStack<C, T>, oldStack: ChildStack<C, T>?): Map<C, AnimationItem<C, T>> =
        when {
            oldStack == null ->
                listOf(AnimationItem(child = newStack.active, direction = Direction.ENTER_FRONT, isInitial = true))

            (newStack.size < oldStack.size) && (newStack.active.configuration in oldStack.backStack) ->
                listOf(
                    AnimationItem(child = newStack.active, direction = Direction.ENTER_BACK, otherChild = oldStack.active),
                    AnimationItem(child = oldStack.active, direction = Direction.EXIT_FRONT, otherChild = newStack.active),
                )

            else ->
                listOf(
                    AnimationItem(child = oldStack.active, direction = Direction.EXIT_BACK, otherChild = newStack.active),
                    AnimationItem(child = newStack.active, direction = Direction.ENTER_FRONT, otherChild = oldStack.active),
                )
        }.associateBy { it.child.configuration }

    private val ChildStack<*, *>.size: Int
        get() = items.size

    private operator fun <C : Any> Iterable<Child<C, *>>.contains(config: C): Boolean =
        any { it.configuration == config }

    protected data class AnimationItem<out C : Any, out T : Any>(
        val child: Child.Created<C, T>,
        val direction: Direction,
        val isInitial: Boolean = false,
        val otherChild: Child.Created<C, T>? = null,
    )
}
