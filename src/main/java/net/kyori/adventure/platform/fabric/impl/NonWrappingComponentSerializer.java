/*
 * This file is part of adventure-platform-fabric, licensed under the MIT License.
 *
 * Copyright (c) 2021 KyoriPowered
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
package net.kyori.adventure.platform.fabric.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import net.kyori.adventure.platform.fabric.impl.accessor.ComponentSerializerAccess;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.GsonHelper;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class NonWrappingComponentSerializer implements ComponentSerializer<Component, Component, net.minecraft.network.chat.Component> {
  public static final NonWrappingComponentSerializer INSTANCE = new NonWrappingComponentSerializer();
  private static @MonotonicNonNull GsonBuilder gsonBuilder;

  public static void initializeGsonBuilder(final @NonNull GsonBuilder gsonBuilder) {
    if(NonWrappingComponentSerializer.gsonBuilder != null) {
      throw new IllegalStateException("Cannot set gsonBuilder a second time!");
    }
    NonWrappingComponentSerializer.gsonBuilder = gsonBuilder;
  }

  private NonWrappingComponentSerializer() {
  }

  @Override
  public Component deserialize(final net.minecraft.network.chat.Component input) {
    if(input instanceof WrappedComponent) {
      return ((WrappedComponent) input).wrapped();
    }

    return ComponentSerializerAccess.getGSON().fromJson(net.minecraft.network.chat.Component.Serializer.toJsonTree(input), Component.class);
  }

  @Override
  public MutableComponent serialize(final Component component) {
    return NonNetworkComponentSerializer.instance.gson.fromJson(ComponentSerializerAccess.getGSON().toJsonTree(component), MutableComponent.class);
  }

  private static final class NonNetworkComponentSerializer {
    private static final NonNetworkComponentSerializer instance = new NonNetworkComponentSerializer();

    private final Gson gson;

    private NonNetworkComponentSerializer() {
      if(gsonBuilder == null) {
        throw new IllegalStateException("gsonBuilder is not initialized!");
      }
      this.gson = gsonBuilder
        .registerTypeHierarchyAdapter(Style.class, new NonNetworkStyleSerializer()) // overwrites existing
        .create();
    }

    private static final class NonNetworkStyleSerializer extends Style.Serializer {
      public @Nullable Style deserialize(final JsonElement jsonElement, final Type type, final JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        if(jsonElement.isJsonObject()) {
          final JsonObject jsonObject = jsonElement.getAsJsonObject();
          if(jsonObject != null) {
            final @Nullable Style style = super.deserialize(jsonElement, type, jsonDeserializationContext);
            if(style != null) {
              return style.withClickEvent(clickEvent(jsonObject));
            }
          }
        }
        return null;
      }

      private static @Nullable ClickEvent clickEvent(final JsonObject style) {
        if(style.has("clickEvent")) {
          final JsonObject clickEvent = GsonHelper.getAsJsonObject(style, "clickEvent");
          final String actionName = GsonHelper.getAsString(clickEvent, "action", null);
          final ClickEvent.@Nullable Action action;
          if(actionName == null) {
            action = null;
          } else {
            action = ClickEvent.Action.getByName(actionName);
          }
          final String value = GsonHelper.getAsString(clickEvent, "value", null);
          if(action != null && value != null/* && action.isAllowedFromServer()*/) {
            return new ClickEvent(action, value);
          }
        }
        return null;
      }
    }
  }
}
