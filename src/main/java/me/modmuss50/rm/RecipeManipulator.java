package me.modmuss50.rm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.commons.io.FilenameUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Mod(name = RecipeManipulator.MOD_NAME, modid = RecipeManipulator.MOD_ID)
public class RecipeManipulator {

	public static final String MOD_ID = "recipemanipulator";
	public static final String MOD_NAME = "RecipeManipulator";
	public static File recipeDir;
	private static Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		recipeDir = new File(Minecraft.getMinecraft().mcDataDir, "recipes");
		if (!recipeDir.exists()) {
			recipeDir.mkdir();
		}
	}

	@Mod.EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		readDirectory(recipeDir);
	}

	private static void readDirectory(File recipeDir) {
		for (File file : recipeDir.listFiles()) {
			if (file.isDirectory()) {
				readDirectory(file);
				continue;
			}
			if (file.getName().endsWith(".json")) {
				readRecipeJson(file);
			}
		}
	}

	private static void readRecipeJson(File recipeFile) {
		String name = FilenameUtils.removeExtension(recipeFile.getName());
		ResourceLocation key = new ResourceLocation(MOD_ID, name);
		try {
			BufferedReader reader = Files.newBufferedReader(recipeFile.toPath());
			JsonObject json = JsonUtils.func_193839_a(GSON, reader, JsonObject.class);
			IRecipe recipe = CraftingHelper.getRecipe(json, new JsonContext(MOD_ID));
			ForgeRegistries.RECIPES.register(recipe.setRegistryName(key));
		} catch (JsonParseException e) {
			throw new Error("Parsing error loading recipe " + key, e);
		} catch (IOException e) {
			throw new Error("Couldn't read recipe " + key + " from " + recipeFile.getName(), e);
		}
	}
}
