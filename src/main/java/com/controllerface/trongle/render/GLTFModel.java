package com.controllerface.trongle.render;

import com.juncture.alloy.models.ModelAsset;
import com.juncture.alloy.models.ModelRegistry;

/// Enumerates all model assets that can be loaded in-game. When new model assets are created, they must be added to the
/// appropriate resource sub-folder specified in the constructor of the [ModelRegistry] object and then added to this enum.
/// The [ModelRegistry] class then uses the enumerated file names to prepare all the assets during the startup process.
public enum GLTFModel implements ModelAsset<GLTFModel>
{
    TEST_CUBE("test_cube.glb"),

    ;

    public final String file_name;

    GLTFModel(String file_name)
    {
        this.file_name = file_name;
    }

    @Override
    public String file_name()
    {
        return file_name;
    }
}
