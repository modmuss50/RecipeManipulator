package me.modmuss50.rm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Mod(name = RecipeManipulator.MOD_NAME, modid = RecipeManipulator.MOD_ID)
public class RecipeManipulator {

	public static final String MOD_ID = "recipemanipulator";
	public static final String MOD_NAME = "RecipeManipulator";
	public static File recipeDir;
	public static File removalFile;
	private static Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static Method loadConstantsMethod = null;

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		recipeDir = new File(Minecraft.getMinecraft().mcDataDir, "recipes");
		if (!recipeDir.exists()) {
			recipeDir.mkdir();
		}
		removalFile = new File(recipeDir, "removal.json");
	}

	@Mod.EventHandler
	public void postInit(FMLPostInitializationEvent event) {
//		try {
//			removeRecipes();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
		readDirectory(recipeDir);
	}

	private static void readDirectory(File recipeDir) {
		for (File file : recipeDir.listFiles()) {
			if (file.isDirectory()) {
				readDirectory(file);
				continue;
			}
			if(file.getName().equals(removalFile.getName()) || file.getName().startsWith("_")){
				continue;
			}
			if (file.getName().endsWith(".json")) {
				readRecipeJson(file);
			}
		}
	}

	//Loosly based off the forge code
	private static void readRecipeJson(File recipeFile) {
		String name = FilenameUtils.removeExtension(recipeFile.getName());
		ResourceLocation key = new ResourceLocation(MOD_ID, name);
		try {
			JsonContext ctx = new JsonContext(MOD_ID);
			BufferedReader reader;

			File constantsFile = new File(recipeFile.getParent(), "_constants.json");
			reader = Files.newBufferedReader(constantsFile.toPath());
			JsonObject[] constantsJson = JsonUtils.func_193839_a(GSON, reader, JsonObject[].class);
			loadConstants(ctx, constantsJson);

			reader = Files.newBufferedReader(recipeFile.toPath());
			JsonObject json = JsonUtils.func_193839_a(GSON, reader, JsonObject.class);
			IRecipe recipe = CraftingHelper.getRecipe(json, ctx);
			ForgeRegistries.RECIPES.register(recipe.setRegistryName(key));

		} catch (JsonParseException e) {
			throw new Error("Parsing error loading recipe " + key, e);
		} catch (IOException e) {
			throw new Error("Couldn't read recipe " + key + " from " + recipeFile.getName(), e);
		}
	}

	private static void loadConstants(JsonContext context, JsonObject[] json){
		try {
			if(loadConstantsMethod == null){
				for(Method method : context.getClass().getDeclaredMethods()){
					if(method.getName().equals("loadConstants")){
						loadConstantsMethod = method;
						loadConstantsMethod.setAccessible(true);
					}
				}
			}
			loadConstantsMethod.invoke(context, new Object[]{json});
		} catch (IllegalAccessException | InvocationTargetException e) {
			throw new Error("Failed to read constants", e);
		}
	}

	private static void removeRecipes() throws IOException {
		if(!removalFile.exists()){
			RemovalFormat format = new RemovalFormat();
			format.recipesToRemove = new ArrayList<>();
			FileUtils.writeStringToFile(removalFile, GSON.toJson(format), Charset.forName("UTF-8"));
		}
		String json = FileUtils.readFileToString(removalFile, Charset.forName("UTF-8"));
		RemovalFormat format = GSON.fromJson(json, RemovalFormat.class);
		for(String string : format.recipesToRemove){
			if(string.contains(":")){
				ResourceLocation resourceLocation = new ResourceLocation(string);
				removeRecipe(recipe -> {
					if(recipe.getRecipeOutput().isEmpty()){
						return false;
					}
					if(recipe.getRecipeOutput().getItem().getRegistryName() == null){
						return false;
					}
					if(recipe.getRecipeOutput().getItem().getRegistryName().equals(resourceLocation)){
						return true;
					}
					return false;
				});
			}
		}
	}

	public static void removeRecipe(Predicate<IRecipe> recipePredicate){
		for(IRecipe recipe : CraftingManager.REGISTRY){
			if(recipePredicate.test(recipe)){
				System.out.println("removing:" + recipe.toString());
			}
		}
	}

	private static class RemovalFormat {
		public List<String> recipesToRemove;
	}

	public static boolean isItemEqual(final ItemStack a, final ItemStack b) {
		if (a == ItemStack.EMPTY || b == ItemStack.EMPTY)
			return false;
		if (a.getItem() != b.getItem())
			return false;
		if (!ItemStack.areItemStackTagsEqual(a, b))
			return false;
		if (a.getHasSubtypes()) {
			if (a.getItemDamage() != b.getItemDamage())
				return false;
		}
		return true;
	}



}
