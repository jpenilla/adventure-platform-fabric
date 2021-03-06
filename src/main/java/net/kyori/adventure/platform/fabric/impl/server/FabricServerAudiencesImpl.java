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
package net.kyori.adventure.platform.fabric.impl.server;

import com.google.common.collect.Iterables;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.platform.fabric.AdventureCommandSourceStack;
import net.kyori.adventure.platform.fabric.impl.AdventureCommandSourceStackInternal;
import net.kyori.adventure.platform.fabric.FabricAudiences;
import net.kyori.adventure.platform.fabric.FabricServerAudiences;
import net.kyori.adventure.platform.fabric.impl.WrappedComponent;
import net.kyori.adventure.platform.fabric.impl.accessor.ComponentSerializerAccess;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.renderer.ComponentRenderer;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * The entry point for accessing Adventure.
 */
public final class FabricServerAudiencesImpl implements FabricServerAudiences {
  private static final Set<FabricServerAudiencesImpl> INSTANCES = Collections.newSetFromMap(new WeakHashMap<>());

  /**
   * Perform an action on every audience provider instance.
   *
   * @param actor a consumer that will be called for every provider
   */
  public static void forEachInstance(final Consumer<FabricServerAudiencesImpl> actor) {
    synchronized(INSTANCES) {
      for(final FabricServerAudiencesImpl instance : INSTANCES) {
        actor.accept(instance);
      }
    }
  }

  private final MinecraftServer server;
  private final ComponentRenderer<Locale> renderer;
  final ServerBossBarListener bossBars;
  private final PlainComponentSerializer plainSerializer;

  public FabricServerAudiencesImpl(final MinecraftServer server, final ComponentRenderer<Locale> renderer) {
    this.server = server;
    this.renderer = renderer;
    this.bossBars = new ServerBossBarListener(this);
    if(FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
      this.plainSerializer = new PlainComponentSerializer(comp -> KeyMapping.createNameSupplier(comp.keybind()).get().getString(), comp -> this.plainSerializer().serialize(this.renderer.render(comp, Locale.getDefault())));
    } else {
      this.plainSerializer = new PlainComponentSerializer(null, comp -> this.plainSerializer().serialize(this.renderer.render(comp, Locale.getDefault())));
    }
    synchronized(INSTANCES) {
      INSTANCES.add(this);
    }
  }

  @Override
  public @NonNull Audience all() {
    return Audience.audience(this.console(), this.players());
  }

  @Override
  public @NonNull Audience console() {
    return this.audience(this.server);
  }

  @Override
  public @NonNull Audience players() {
    return Audience.audience(this.audiences(this.server.getPlayerList().getPlayers()));
  }

  @Override
  public @NonNull Audience player(final @NonNull UUID playerId) {
    final @Nullable ServerPlayer player = this.server.getPlayerList().getPlayer(playerId);
    return player != null ? this.audience(player) : Audience.empty();
  }

  private Iterable<Audience> audiences(final Iterable<? extends ServerPlayer> players) {
    return Iterables.transform(players, this::audience);
  }

  @Override
  public @NonNull Audience permission(final @NonNull String permission) {
    return Audience.empty(); // TODO: permissions api
  }

  @Override public AdventureCommandSourceStack audience(final @NonNull CommandSourceStack source) {
    if(!(source instanceof AdventureCommandSourceStackInternal)) {
      throw new IllegalArgumentException("The AdventureCommandSource mixin failed!");
    }

    final AdventureCommandSourceStackInternal internal = (AdventureCommandSourceStackInternal) source;
    return internal.adventure$audience(this.audience(internal.adventure$source()), this);
  }

  @Override public Audience audience(final @NonNull CommandSource source) {
    if(source instanceof RenderableAudience) {
      return ((RenderableAudience) source).renderUsing(this);
    } else if(source instanceof Audience) {
      // TODO: How to pass component renderer through
      return (Audience) source;
    } else {
      return new CommandSourceAudience(source, this);
    }
  }

  @Override public Audience audience(final @NonNull Iterable<ServerPlayer> players) {
    return Audience.audience(this.audiences(players));
  }

  @Override
  public @NonNull Audience world(final @NonNull Key worldId) {
    final @Nullable ServerLevel level = this.server.getLevel(ResourceKey.create(Registry.DIMENSION_REGISTRY,
      FabricAudiences.toNative(requireNonNull(worldId, "worldId"))));
    if(level != null) {
      return this.audience(level.players());
    }
    return Audience.empty();
  }

  @Override
  public @NonNull Audience server(final @NonNull String serverName) {
    return Audience.empty();
  }

  @Override
  public PlainComponentSerializer plainSerializer() {
    return this.plainSerializer;
  }

  @Override
  public @NonNull ComponentRenderer<Locale> localeRenderer() {
    return this.renderer;
  }

  @Override
  public net.minecraft.network.chat.Component toNative(final Component adventure) {
    if(adventure == Component.empty()) return TextComponent.EMPTY;

    return new WrappedComponent(requireNonNull(adventure, "adventure"), this.renderer);
  }

  @Override
  public Component toAdventure(final net.minecraft.network.chat.Component vanilla) {
    if(vanilla instanceof WrappedComponent) {
      return ((WrappedComponent) vanilla).wrapped();
    } else {
      return ComponentSerializerAccess.getGSON().fromJson(net.minecraft.network.chat.Component.Serializer.toJsonTree(vanilla), Component.class);
    }
  }

  public ServerBossBarListener bossBars() {
    return this.bossBars;
  }

  @Override
  public void close() {
  }
}
