# How to reproduce the CMP 1.11.0 desktop a11y NPE

A standalone Compose Multiplatform Desktop project that produces a
`NullPointerException` inside
`ComposeSceneAccessibility.defaultAccessibilityFocusTarget` on macOS.

## Quick start

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :desktopApp:run
```

Click on a song in listPane. It will show info in the detailPane. Click on "Show media". An extraPane
open. Click on "Close".

## Expected output

```
Exception in thread "main" java.lang.NullPointerException
    at java.base/java.util.ArrayDeque.addFirst(ArrayDeque.java:285)
    at androidx.compose.ui.platform.a11y.ComposeSceneAccessibility
       .defaultAccessibilityFocusTarget(ComposeSceneAccessibility.kt:169)
    at androidx.compose.ui.platform.a11y.ComposeSceneAccessibility
       .onAccessibleReceivedFocus(ComposeSceneAccessibility.kt:86)
    at androidx.compose.ui.platform.a11y.ComposeSceneAccessibility
       .access$onAccessibleReceivedFocus(ComposeSceneAccessibility.kt:51)
    at androidx.compose.ui.platform.a11y.ComposeSceneAccessibility
       $onSemanticsOwnerAppended$ownerAccessibility$1.invoke(ComposeSceneAccessibility.kt:99)
    [...]
    at androidx.compose.ui.platform.a11y.SemanticsOwnerAccessibility
       .onNodeRemoved(SemanticsOwnerAccessibility.kt:169)
    at androidx.compose.ui.platform.a11y.SemanticsOwnerAccessibility
       .syncNodes(SemanticsOwnerAccessibility.kt:421)
    [... EDT pump ...]
    at java.desktop/java.awt.EventDispatchThread.run(EventDispatchThread.java:92)
```

If you got that, the bug reproduced.
