package app.morphe.patches.reddit.layout.rall

import app.morphe.patcher.patch.resourcePatch
// TODO: use the same imports/signatures as the other Reddit patches in your repo for these two:


import org.w3c.dom.Element
import org.w3c.dom.NodeList
import app.morphe.patches.reddit.misc.extension.hooks.redditActivityOnCreateHook
import app.morphe.patches.reddit.misc.extension.hooks.redditApplicationOnCreateHook
import app.morphe.patches.all.misc.extension.sharedExtensionPatch

private const val EXTENSION_PACKAGE = "app.morphe.extension.reddit.rall"
private const val ACTIVITY_CLASS = "$EXTENSION_PACKAGE.RAllActivity"
private const val SHORTCUTS_META = "android.app.shortcuts"
private const val OWN_SHORTCUTS_XML = "morphe_rall_shortcuts"
private const val LABEL_NAME = "morphe_rall_shortcut_label"

private fun NodeList.elements(): List<Element> =
    (0 until length).mapNotNull { item(it) as? Element }

private fun Element.children(tag: String): List<Element> =
    childNodes.elements().filter { it.tagName == tag }

private fun Element.isLauncher(): Boolean =
    getElementsByTagName("intent-filter").elements().any { filter ->
        val actions = filter.getElementsByTagName("action").elements().map { it.getAttribute("android:name") }
        val categories = filter.getElementsByTagName("category").elements().map { it.getAttribute("android:name") }
        "android.intent.action.MAIN" in actions && "android.intent.category.LAUNCHER" in categories
    }

private fun org.w3c.dom.Document.rAllShortcut(packageName: String): Element =
    createElement("shortcut").apply {
        setAttribute("android:shortcutId", "morphe_rall")
        setAttribute("android:enabled", "true")
        setAttribute("android:shortcutShortLabel", "@string/$LABEL_NAME")
        setAttribute("android:shortcutLongLabel", "@string/$LABEL_NAME")
        appendChild(
            createElement("intent").apply {
                setAttribute("android:action", "android.intent.action.VIEW")
                setAttribute("android:targetPackage", packageName)
                setAttribute("android:targetClass", ACTIVITY_CLASS)
            }
        )
    }

@Suppress("unused")
val restoreRAllPatch = resourcePatch(
    name = "Restore r/all",
    description = "Adds an \"r/all\" shortcut (long-press the app icon) that opens old.reddit.com/r/all, " +
        "since Reddit removed r/all from the app.",
    default = true,
) {
    // Merges the extension dex, which contains RAllActivity.
    dependsOn(sharedExtensionPatch())
    compatibleWith(COMPATIBILITY_REDDIT)

    // TODO: copy the compatibility declaration from another Reddit patch in your repo
    // (the exact API differs between patcher versions).

    execute {
        var packageName = ""
        var needsOwnShortcutsFile = false
        val existingShortcutFiles = mutableSetOf<String>()

        document("AndroidManifest.xml").use { doc ->
            packageName = doc.documentElement.getAttribute("package")
            val application = doc.getElementsByTagName("application").item(0) as Element

            // Collect launcher components first, before our own activity is added to the tree.
            val launchers = (
                application.getElementsByTagName("activity").elements() +
                    application.getElementsByTagName("activity-alias").elements()
                ).filter { it.isLauncher() }

            // 1. Register the trampoline activity.
            application.appendChild(
                doc.createElement("activity").apply {
                    setAttribute("android:name", ACTIVITY_CLASS)
                    setAttribute("android:exported", "true")
                    setAttribute("android:excludeFromRecents", "true")
                    setAttribute("android:noHistory", "true")
                    setAttribute("android:taskAffinity", "")
                    setAttribute("android:theme", "@android:style/Theme.Translucent.NoTitleBar")
                }
            )

            // 2. Make every launcher entry point (Reddit uses aliases for its icon variants)
            //    reference a shortcuts file, reusing the app's own file if it already has one.
            launchers.forEach { launcher ->
                val existing = launcher.children("meta-data")
                    .firstOrNull { it.getAttribute("android:name") == SHORTCUTS_META }

                if (existing != null) {
                    existingShortcutFiles += existing.getAttribute("android:resource").removePrefix("@xml/")
                } else {
                    needsOwnShortcutsFile = true
                    launcher.appendChild(
                        doc.createElement("meta-data").apply {
                            setAttribute("android:name", SHORTCUTS_META)
                            setAttribute("android:resource", "@xml/$OWN_SHORTCUTS_XML")
                        }
                    )
                }
            }
        }

        // 3. Shortcut definitions.
        if (needsOwnShortcutsFile) {
            get("res/xml/$OWN_SHORTCUTS_XML.xml").apply {
                parentFile?.mkdirs()
                writeText(
                    """
                    <?xml version="1.0" encoding="utf-8"?>
                    <shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
                        <shortcut
                            android:shortcutId="morphe_rall"
                            android:enabled="true"
                            android:shortcutShortLabel="@string/$LABEL_NAME"
                            android:shortcutLongLabel="@string/$LABEL_NAME">
                            <intent
                                android:action="android.intent.action.VIEW"
                                android:targetPackage="$packageName"
                                android:targetClass="$ACTIVITY_CLASS" />
                        </shortcut>
                    </shortcuts>
                    """.trimIndent()
                )
            }
        }

        existingShortcutFiles.forEach { name ->
            document("res/xml/$name.xml").use { doc ->
                doc.documentElement.appendChild(doc.rAllShortcut(packageName))
            }
        }

        // 4. Label (shortcut labels must be string resources).
        document("res/values/strings.xml").use { doc ->
            doc.documentElement.appendChild(
                doc.createElement("string").apply {
                    setAttribute("name", LABEL_NAME)
                    textContent = "r/all"
                }
            )
        }
    }
}
