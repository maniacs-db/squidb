/*
 * Copyright 2015, Yahoo Inc.
 * Copyrights licensed under the Apache 2.0 License.
 * See the accompanying LICENSE file for terms.
 */
package com.yahoo.squidb.processor.plugins;

import com.yahoo.aptutils.utils.AptUtils;
import com.yahoo.squidb.processor.data.ModelSpec;
import com.yahoo.squidb.processor.plugins.defaults.ConstantCopyingPlugin;
import com.yahoo.squidb.processor.plugins.defaults.ConstructorPlugin;
import com.yahoo.squidb.processor.plugins.defaults.ImplementsPlugin;
import com.yahoo.squidb.processor.plugins.defaults.ModelMethodPlugin;
import com.yahoo.squidb.processor.plugins.defaults.properties.InheritedModelSpecFieldPlugin;
import com.yahoo.squidb.processor.plugins.defaults.properties.TableModelSpecFieldPlugin;
import com.yahoo.squidb.processor.plugins.defaults.properties.ViewModelSpecFieldPlugin;

import java.util.ArrayList;
import java.util.List;

import javax.tools.Diagnostic;

/**
 * This class maintains a list of known/enabled {@link Plugin} classes. Plugins available by default include
 * {@link ConstructorPlugin} for generating model constructors, {@link ImplementsPlugin} for allowing models to
 * implement interfaces, {@link ModelMethodPlugin} for copying methods from the model spec to the model, and the three
 * property generator plugins ({@link TableModelSpecFieldPlugin}, {@link ViewModelSpecFieldPlugin}, and
 * {@link InheritedModelSpecFieldPlugin}).
 * <p>
 * This class also manages option flags for plugins. The default plugins for constructors, interfaces, and model
 * methods can all be disabled using a flag. Other flags allow disabling default content values, disabling the
 * convenience getters and setters that accompany each property, and preferring user-defined plugins to the default
 * plugins. Options are passed as a bitmask to the processor using the key "squidbOptions".
 */
public class PluginManager {

    private final AptUtils utils;
    private final int optionFlags;
    private List<Class<? extends Plugin>> highPriorityPlugins = new ArrayList<Class<? extends Plugin>>();
    private List<Class<? extends Plugin>> normalPriorityPlugins = new ArrayList<Class<? extends Plugin>>();
    private List<Class<? extends Plugin>> lowPriorityPlugins = new ArrayList<Class<? extends Plugin>>();

    public enum PluginPriority {
        LOW,
        NORMAL,
        HIGH
    }

    /**
     * Flag for disabling the default constructors generated in each model class
     */
    public static final int OPTIONS_DISABLE_DEFAULT_CONSTRUCTORS = 1;

    /**
     * Flag for disabling default processing of the {@link com.yahoo.squidb.annotations.Implements} annotation for
     * declaring that models implement interfaces
     */
    public static final int OPTIONS_DISABLE_DEFAULT_IMPLEMENTS_HANDLING = 1 << 1;

    /**
     * Flag for disabling the default copying of static methods and model methods from the spec to the model class
     */
    public static final int OPTIONS_DISABLE_DEFAULT_METHOD_HANDLING = 1 << 2;

    /**
     * Flag for disabling the default copying of public static final fields as constants to the generated model
     */
    public static final int OPTIONS_DISABLE_DEFAULT_CONSTANT_COPYING = 1 << 3;

    /**
     * Flag for disabling the in-memory default content values used as for fallback values in empty models
     */
    public static final int OPTIONS_DISABLE_DEFAULT_CONTENT_VALUES = 1 << 4;

    /**
     * Flag for disabling the convenience getters and setters generated by default with each property
     */
    public static final int OPTIONS_DISABLE_DEFAULT_GETTERS_AND_SETTERS = 1 << 5;

    /**
     * @param utils annotation processing utilities class
     * @param optionFlags option flags for disabling default plugins
     */
    public PluginManager(AptUtils utils, int optionFlags) {
        this.utils = utils;
        this.optionFlags = optionFlags;
        initializeDefaultPlugins();
    }

    private void initializeDefaultPlugins() {
        if (!getFlag(OPTIONS_DISABLE_DEFAULT_CONSTRUCTORS)) {
            normalPriorityPlugins.add(ConstructorPlugin.class);
        }
        if (!getFlag(OPTIONS_DISABLE_DEFAULT_IMPLEMENTS_HANDLING)) {
            normalPriorityPlugins.add(ImplementsPlugin.class);
        }
        if (!getFlag(OPTIONS_DISABLE_DEFAULT_METHOD_HANDLING)) {
            normalPriorityPlugins.add(ModelMethodPlugin.class);
        }

        // Can't disable these, but they can be overridden by user plugins if the OPTIONS_PREFER_USER_PLUGIN flag is set
        normalPriorityPlugins.add(TableModelSpecFieldPlugin.class);
        normalPriorityPlugins.add(ViewModelSpecFieldPlugin.class);
        normalPriorityPlugins.add(InheritedModelSpecFieldPlugin.class);

        if (!getFlag(OPTIONS_DISABLE_DEFAULT_CONSTANT_COPYING)) {
            // This plugin claims any public static final fields not handled by the other plugins and copies them to the
            // generated model
            normalPriorityPlugins.add(ConstantCopyingPlugin.class);
        }
    }

    /**
     * @param flag the flag to check
     * @return true if the flag is set in the options bitmask, false otherwise
     */
    public boolean getFlag(int flag) {
        return (optionFlags & flag) > 0;
    }

    /**
     * Add a {@link Plugin} class to the list of known plugins
     *
     * @param plugin the plugin class
     * @param priority the priority to give the plugin
     */
    public void addPlugin(Class<? extends Plugin> plugin, PluginPriority priority) {
        utils.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "Adding plugin class " + plugin.toString() + " with priority " + priority);
        switch (priority) {
            case LOW:
                lowPriorityPlugins.add(plugin);
                break;
            case HIGH:
                highPriorityPlugins.add(plugin);
                break;
            case NORMAL:
            default:
                normalPriorityPlugins.add(plugin);
                break;
        }
    }

    /**
     * @param modelSpec the model spec the Plugins will be instantiated for
     * @return a new {@link PluginBundle} containing Plugins initialized to handle the given model spec
     */
    public PluginBundle getPluginBundleForModelSpec(ModelSpec<?> modelSpec) {
        List<Plugin> plugins = new ArrayList<Plugin>();
        accumulatePlugins(plugins, highPriorityPlugins, modelSpec);
        accumulatePlugins(plugins, normalPriorityPlugins, modelSpec);
        accumulatePlugins(plugins, lowPriorityPlugins, modelSpec);
        return new PluginBundle(plugins, utils);
    }

    private void accumulatePlugins(List<Plugin> accumulator, List<Class<? extends Plugin>> pluginList,
            ModelSpec<?> modelSpec) {
        for (Class<? extends Plugin> plugin : pluginList) {
            try {
                accumulator.add(plugin.getConstructor(ModelSpec.class, AptUtils.class).newInstance(modelSpec, utils));
            } catch (Exception e) {
                utils.getMessager().printMessage(Diagnostic.Kind.WARNING,
                        "Unable to instantiate plugin " + plugin + ", reason: " + e);
            }
        }
    }
}
