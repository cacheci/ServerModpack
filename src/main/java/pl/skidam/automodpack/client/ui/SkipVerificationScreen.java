package pl.skidam.automodpack.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

import pl.skidam.automodpack.client.ui.versioned.VersionedMatrices;
import pl.skidam.automodpack.client.ui.versioned.VersionedScreen;
import pl.skidam.automodpack.client.ui.versioned.VersionedText;
import pl.skidam.automodpack_core.utils.UriOpener;
import pl.skidam.automodpack_loader_core.screen.ScreenManager;

public class SkipVerificationScreen extends VersionedScreen {
	private final Screen verificationScreen;
	private final Runnable validatedCallback;
	private Button backButton;
	private Button confirmButton;
	private Button wikiButton;

	public SkipVerificationScreen(Screen verificationScreen, Runnable validatedCallback) {
		super(VersionedText.literal("SkipVerificationScreen"));
		this.verificationScreen = verificationScreen;
		this.validatedCallback = validatedCallback;
	}

	@Override
	protected void init() {
		super.init();

		initWidgets();

		this.addRenderableWidget(this.backButton);
		this.addRenderableWidget(this.confirmButton);
		this.addRenderableWidget(this.wikiButton);
	}

	public void initWidgets() {
		assert this.minecraft != null;

		this.backButton = buttonWidget(this.width / 2 - 155, this.height / 2 + 80, 150, 20,
				VersionedText.translatable("automodpack.back"),
				button -> {
					assert this.minecraft != null;
					this.minecraft.gui.setScreen(verificationScreen);
				});

		this.confirmButton = buttonWidget(this.width / 2 + 5, this.height / 2 + 80, 150, 20,
				VersionedText.translatable("automodpack.skip"),
				button -> confirmSkip());

		this.wikiButton = iconButtonWidget(this.width / 2 + 22 + 150, this.height / 2 + 15, 20, 16,
				button -> UriOpener.openUri("https://moddedmc.wiki/en/project/automodpack/latest/docs/technicals/certificate"),
				"link");

		setTooltip(wikiButton, VersionedText.translatable("automodpack.learnmore"));
	}

	private void confirmSkip() {
		confirmButton.active = false;
		new ScreenManager().waiting();
		validatedCallback.run();
	}

	@Override
	public void versionedRender(VersionedMatrices matrices, int mouseX, int mouseY, float delta) {
		int lineHeight = 12; // Consistent line spacing

		// Warning title
		drawCenteredTextWithShadow(matrices, this.font,
				VersionedText.translatable("automodpack.validation.skip.title").withStyle(ChatFormatting.BOLD),
				this.width / 2, this.height / 2 - 85, TextColors.LIGHT_RED);

		// Warning message line 1
		drawCenteredTextWithShadow(matrices, this.font,
				VersionedText.translatable("automodpack.validation.skip.warning1"),
				this.width / 2, this.height / 2 - 65, TextColors.WHITE);

		// Warning message line 2
		drawCenteredTextWithShadow(matrices, this.font,
				VersionedText.translatable("automodpack.validation.skip.warning2"),
				this.width / 2, this.height / 2 - 65 + lineHeight, TextColors.LIGHT_RED);

		// Instructions
		drawCenteredTextWithShadow(matrices, this.font,
				VersionedText.translatable("automodpack.validation.skip.instruction"),
				this.width / 2, this.height / 2 - 35, TextColors.WHITE);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
