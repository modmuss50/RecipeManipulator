package me.modmuss50.rm;

import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IRecipeRegistry;
import mezz.jei.api.JEIPlugin;
import net.minecraft.item.crafting.IRecipe;

@JEIPlugin
public class RecipeManipulatorJEI implements IModPlugin {

	public static IRecipeRegistry recipeRegistry;

	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		recipeRegistry = jeiRuntime.getRecipeRegistry();
	}


	public static void removeRecipe(IRecipe recipe) {
		if(recipeRegistry == null){
			return;
		}
		recipeRegistry.removeRecipe(recipe);
	}

	public static void addRecipe(IRecipe recipe) {
		if(recipeRegistry == null){
			return;
		}
		recipeRegistry.addRecipe(recipe);
	}

}
