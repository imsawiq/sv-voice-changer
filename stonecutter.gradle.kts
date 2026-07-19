plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.8-fabric"

stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    properties {
        tags(version, loader)
    }

    constants {
        match(loader, "fabric", "neoforge")
    }

    replacements {
        string(current.parsed >= "26.1") {
            replace("client.keybinding.v1", "client.keymapping.v1")
            replace("KeyBindingHelper", "KeyMappingHelper")
            replace("registerKeyBinding", "registerKeyMapping")
            replace("GuiGraphics", "GuiGraphicsExtractor")
        }
    }
}
