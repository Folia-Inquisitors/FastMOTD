/*
 * Copyright (C) 2022 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.fastmotd.holder;

import com.google.common.primitives.Bytes;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.elytrium.fastmotd.Settings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class MOTDHolder {

  private final ComponentSerializer<Component, Component, String> serializer;
  private final ByteBuf byteBuf;
  private final int maxOnlineDigit;
  private final int onlineDigit;
  private final int protocolDigit;

  public MOTDHolder(ComponentSerializer<Component, Component, String> serializer, String descriptionSerialized, String favicon) {
    this.serializer = serializer;

    String name = Settings.IMP.MAIN.VERSION_NAME.replace("\"", "\\\"");
    String description = this.toLegacy(descriptionSerialized.replace("{NL}", "\\n"));

    StringBuilder motd = new StringBuilder("{\"players\":{\"max\":    0,\"online\":    1,\"sample\":[");

    List<String> information = Settings.IMP.MAIN.INFORMATION;
    int lastIdx = information.size() - 1;
    if (lastIdx > 9) {
      lastIdx = 9;
    }

    for (int i = 0; i < lastIdx; i++) {
      String e = information.get(i);
      motd.append("{\"id\":\"00000000-0000-0000-0000-00000000000").append(i).append("\",\"name\":\"").append(this.toLegacy(e)).append("\"},");
    }

    motd.append("{\"id\":\"00000000-0000-0000-0000-000000000009\",\"name\":\"")
        .append(this.toLegacy(information.get(lastIdx)))
        .append("\"}]},\"description\":{\"text\":\"")
        .append(description)
        .append("\"},\"version\":{\"name\":\"")
        .append(name)
        .append("\",\"protocol\":         },\"favicon\":\"")
        .append(favicon)
        .append("\"}");

    byte[] bytes = motd.toString().getBytes(StandardCharsets.UTF_8);
    int varIntLength = ProtocolUtils.varIntBytes(bytes.length);
    int length = bytes.length + varIntLength + 1;
    int lengthOfLength = ProtocolUtils.varIntBytes(length);
    varIntLength += lengthOfLength;

    this.maxOnlineDigit = Bytes.indexOf(bytes, "    0".getBytes(StandardCharsets.UTF_8)) + 1 + varIntLength;
    this.onlineDigit = Bytes.indexOf(bytes, "    1".getBytes(StandardCharsets.UTF_8)) + 1 + varIntLength;
    this.protocolDigit = Bytes.indexOf(bytes, "protocol\":         }".getBytes(StandardCharsets.UTF_8)) + 19 + varIntLength;

    this.byteBuf = Unpooled.directBuffer(length + lengthOfLength);
    ProtocolUtils.writeVarInt(this.byteBuf, length);
    this.byteBuf.writeByte(0);
    ProtocolUtils.writeVarInt(this.byteBuf, bytes.length);
    this.byteBuf.writeBytes(bytes);
  }

  public void replaceOnline(int max, int online) {
    this.localReplaceOnline(this.maxOnlineDigit, max);
    this.localReplaceOnline(this.onlineDigit, online);
  }

  private void localReplaceOnline(int digit, int to) {
    this.byteBuf.setByte(digit, to >= 10000 ? (to / 10000 % 10) + '0' : ' ');
    this.byteBuf.setByte(digit + 1, to >= 1000 ? (to / 1000 % 10) + '0' : ' ');
    this.byteBuf.setByte(digit + 2, to >= 100 ? (to / 100 % 10) + '0' : ' ');
    this.byteBuf.setByte(digit + 3, to >= 10 ? (to / 10 % 10) + '0' : ' ');
    this.byteBuf.setByte(digit + 4, (to % 10) + '0');
  }

  private String toLegacy(String from) {
    return LegacyComponentSerializer.legacySection().serialize(this.serializer.deserialize(from));
  }

  public ByteBuf getByteBuf(ProtocolVersion version) {
    ByteBuf buf = this.byteBuf.copy();
    int protocol = version.getProtocol();
    this.replaceStrInt(buf, this.protocolDigit, protocol);
    return buf;
  }

  private void replaceStrInt(ByteBuf buf, int startIndex, int toSet) {
    while (toSet > 0) {
      buf.setByte(startIndex--, (toSet % 10) + '0');
      toSet /= 10;
    }
  }

  public void dispose() {
    this.byteBuf.release();
  }
}