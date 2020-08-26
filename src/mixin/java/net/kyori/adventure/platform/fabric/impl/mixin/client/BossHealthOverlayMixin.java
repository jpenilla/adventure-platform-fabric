/*
 * This file is part of adventure, licensed under the MIT License.
 *
 * Copyright (c) 2020 KyoriPowered
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.kyori.adventure.platform.fabric.impl.mixin.client;

import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.platform.fabric.impl.client.BossHealthOverlayBridge;
import net.kyori.adventure.platform.fabric.impl.client.ClientBossBarListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin implements BossHealthOverlayBridge {

  @Shadow @Final private Map<UUID, LerpingBossEvent> events;

  private ClientBossBarListener adventure$listener;

  @Inject(method = "<init>", at = @At("RETURN"))
  private void initListener(final Minecraft client, final CallbackInfo ci) {
    this.adventure$listener = new ClientBossBarListener(this.events);
  }

  @Override
  public ClientBossBarListener adventure$listener() {
    return this.adventure$listener;
  }

  @Inject(method = "reset", at = @At("HEAD"))
  private void resetListener(final CallbackInfo ci) {
    if(this.adventure$listener != null) {
      this.adventure$listener.clear();
    }
  }
}