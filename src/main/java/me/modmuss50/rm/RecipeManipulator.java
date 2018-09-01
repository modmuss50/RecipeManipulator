package me.modmuss50.rm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.JsonContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.RegistryManager;
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
		recipeDir = new File(event.getModConfigurationDirectory().getParent(), "recipes");
		if (!recipeDir.exists()) {
			recipeDir.mkdir();
		}
		removalFile = new File(recipeDir, "removal.json");
	}

	@Mod.EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		try {
			removeRecipes();
		} catch (IOException e) {
			e.printStackTrace();
		}
		readDirectory(recipeDir);
	}

	@Mod.EventHandler
	public void serverStart(FMLServerStartingEvent event){
		event.registerServerCommand(new CommandBase() {
			@Override
			public String getName() {
				return "recipe_reload";
			}

			@Override
			public String getUsage(ICommandSender sender) {
				return getName();
			}

			@Override
			public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
				long start = System.currentTimeMillis();
				Minecraft.getMinecraft().addScheduledTask(RecipeManipulator::reload);
				sender.sendMessage(new TextComponentString("Recipes reloaded in " + (System.currentTimeMillis() - start) + "ms"));
			}
		});
	}

	private static void readDirectory(File recipeDir) {
		for (File file : recipeDir.listFiles()) {
			if (file.isDirectory()) {
				readDirectory(file);
				continue;
			}
			if (file.getName().equals(removalFile.getName()) || file.getName().startsWith("_")) {
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
			if(constantsFile.exists()){
				reader = Files.newBufferedReader(constantsFile.toPath());
				JsonObject[] constantsJson = JsonUtils.fromJson(GSON, reader, JsonObject[].class);
				loadConstants(ctx, constantsJson);
			}

			reader = Files.newBufferedReader(recipeFile.toPath());
			JsonObject json = JsonUtils.fromJson(GSON, reader, JsonObject.class);
			IRecipe recipe = CraftingHelper.getRecipe(json, ctx);
			addRecipe(recipe.setRegistryName(key));
			reader.close();

		} catch (JsonParseException e) {
			throw new Error("Parsing error loading recipe " + key, e);
		} catch (IOException e) {
			throw new Error("Couldn't read recipe " + key + " from " + recipeFile.getName(), e);
		}
	}

	private static void loadConstants(JsonContext context, JsonObject[] json) {
		try {
			if (loadConstantsMethod == null) {
				for (Method method : context.getClass().getDeclaredMethods()) {
					if (method.getName().equals("loadConstants")) {
						loadConstantsMethod = method;
						loadConstantsMethod.setAccessible(true);
					}
				}
			}
			loadConstantsMethod.invoke(context, new Object[] { json });
		} catch (IllegalAccessException | InvocationTargetException e) {
			throw new Error("Failed to read constants", e);
		}
	}

	private static void removeRecipes() throws IOException {
		if (!removalFile.exists()) {
			RemovalFormat format = new RemovalFormat();
			format.recipesToRemove = new ArrayList<>();
			FileUtils.writeStringToFile(removalFile, GSON.toJson(format), Charset.forName("UTF-8"));
		}
		String json = FileUtils.readFileToString(removalFile, Charset.forName("UTF-8"));
		RemovalFormat format = GSON.fromJson(json, RemovalFormat.class);
		for (String string : format.recipesToRemove) {
			if (string.startsWith("item@") || string.startsWith("block@") && string.contains(":")) {
				final String[] split = string.replace("block@","").replace("item@", "").split(":");
				if(split.length < 2){
					throw new RuntimeException("Recipe removal.json file is invalid, line: " + string);
				}
				final boolean metaData = split.length == 3;
				ResourceLocation resourceLocation = new ResourceLocation(split[0], split[1]);
				removeRecipe(recipe -> {
					if (recipe.getRecipeOutput().isEmpty()) {
						return false;
					}
					if (recipe.getRecipeOutput().getItem().getRegistryName() == null) {
						return false;
					}
					if(!metaData){
						if (recipe.getRecipeOutput().getItem().getRegistryName().equals(resourceLocation)) {
							return true;
						}
					} else {
						int meta = Integer.parseInt(split[2]);
						ItemStack output = recipe.getRecipeOutput();
						if (output.getItem().getRegistryName().equals(resourceLocation)) {
							if(output.getMetadata() == meta){
								return true;
							}
						}
					}

					return false;
				});
			} else if (string.startsWith("ore@")){
				String oreName = string.replace("ore@", "");
				removeRecipe(recipe -> {
					if(!OreDictionary.doesOreNameExist(oreName)){
						return false;
					}
					if (recipe.getRecipeOutput().isEmpty()) {
						return false;
					}
					if (recipe.getRecipeOutput().getItem().getRegistryName() == null) {
						return false;
					}
					ItemStack output = recipe.getRecipeOutput();
					NonNullList<ItemStack> ores = OreDictionary.getOres((oreName));
					for (ItemStack stack : ores) {
						if (isItemEqual(stack, output)) {
							return true;
						}
					}
					return false;
				});
			}
		}
	}

	public static void removeRecipe(Predicate<IRecipe> recipePredicate) {
		for (IRecipe recipe : CraftingManager.REGISTRY) {
			if (recipePredicate.test(recipe)) {
				removeRecipe(recipe);
			}
		}
	}

	public static List<IRecipe> removedRecipes = new ArrayList<>();
	public static List<IRecipe> addedRecipes = new ArrayList<>();

	public static void removeRecipe(IRecipe recipe) {
		removedRecipes.add(recipe);
		RegistryManager.ACTIVE.getRegistry(GameData.RECIPES).remove(recipe.getRegistryName());
		RecipeManipulatorJEI.removeRecipe(recipe);
	}

	public static void addRecipe(IRecipe recipe){
		addedRecipes.add(recipe);
		ForgeRegistries.RECIPES.register(recipe);
		RecipeManipulatorJEI.addRecipe(recipe);
	}

	public static void reload(){
		RegistryManager.ACTIVE.getRegistry(GameData.RECIPES).unfreeze();
		undoRecipeRemoval();
		undoRecipeAddition();
		readDirectory(recipeDir);
		RegistryManager.ACTIVE.getRegistry(GameData.RECIPES).freeze();
	}

	public static void undoRecipeRemoval(){
		removedRecipes.forEach(ForgeRegistries.RECIPES::register);
		removedRecipes.forEach(RecipeManipulatorJEI::addRecipe);
		removedRecipes.clear();
	}

	public static void undoRecipeAddition(){
		addedRecipes.forEach(recipe -> Minecraft.getMinecraft().player.recipeBook.lock(recipe)); //Client
		addedRecipes.forEach(recipe -> {
			IntegratedServer server = Minecraft.getMinecraft().getIntegratedServer();
			server.getPlayerList().getPlayers().forEach(entityPlayerMP -> entityPlayerMP.getRecipeBook().lock(recipe));
		}); //Server
		addedRecipes.forEach(RecipeManipulatorJEI::removeRecipe);
		addedRecipes.forEach(recipe -> RegistryManager.ACTIVE.getRegistry(GameData.RECIPES).remove(recipe.getRegistryName()));
		addedRecipes.clear();
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
