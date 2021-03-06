/*
 * Copyright 2019 Lightbend Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package docs.user.eventsourced.behavior;

import com.example.Domain;
import com.example.Shoppingcart;
import com.google.protobuf.Empty;
import io.cloudstate.javasupport.EntityId;
import io.cloudstate.javasupport.eventsourced.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

// #content
@EventSourcedEntity
public class ShoppingCartEntity {
  private final Map<String, Shoppingcart.LineItem> cart = new LinkedHashMap<>();
  private boolean checkedout = false;

  public ShoppingCartEntity(EventSourcedEntityCreationContext ctx) {}

  @CommandHandler
  public Empty addItem(Shoppingcart.AddLineItem item, CommandContext ctx) {
    if (!checkedout) {
      if (item.getQuantity() <= 0) {
        ctx.fail("Cannot add negative quantity of to item" + item.getProductId());
      }
      ctx.emit(
          Domain.ItemAdded.newBuilder()
              .setItem(
                  Domain.LineItem.newBuilder()
                      .setProductId(item.getProductId())
                      .setName(item.getName())
                      .setQuantity(item.getQuantity())
                      .build())
              .build());
      return Empty.getDefaultInstance();
    } else {
      throw ctx.fail("Can't add more items to an already checked out shopping cart");
    }
  }

  @CommandHandler
  public Empty checkout(CommandContext ctx) {
    if (!checkedout) {
      ctx.emit(Domain.CheckedOut.getDefaultInstance());
      return Empty.getDefaultInstance();
    } else {
      throw ctx.fail("Shopping cart is already checked out");
    }
  }

  @EventHandler
  public void itemAdded(Domain.ItemAdded itemAdded) {
    Shoppingcart.LineItem item = cart.get(itemAdded.getItem().getProductId());
    if (item == null) {
      item = convert(itemAdded.getItem());
    } else {
      item =
          item.toBuilder()
              .setQuantity(item.getQuantity() + itemAdded.getItem().getQuantity())
              .build();
    }
    cart.put(item.getProductId(), item);
  }

  @EventHandler(eventClass = Domain.CheckedOut.class)
  public void checkedOut(EventContext ctx) {
    checkedout = true;
  }

  @CommandHandler
  public Shoppingcart.Cart getCart() {
    return Shoppingcart.Cart.newBuilder().addAllItems(cart.values()).build();
  }

  @Snapshot
  public Domain.Cart snapshot() {
    return Domain.Cart.newBuilder()
        .addAllItems(cart.values().stream().map(this::convert).collect(Collectors.toList()))
        .build();
  }

  @SnapshotHandler
  public void handleSnapshot(Domain.Cart cart, SnapshotContext ctx) {
    this.cart.clear();
    for (Domain.LineItem item : cart.getItemsList()) {
      this.cart.put(item.getProductId(), convert(item));
    }
    this.checkedout = cart.getCheckedout();
  }

  private Shoppingcart.LineItem convert(Domain.LineItem item) {
    return Shoppingcart.LineItem.newBuilder()
        .setProductId(item.getProductId())
        .setName(item.getName())
        .setQuantity(item.getQuantity())
        .build();
  }

  private Domain.LineItem convert(Shoppingcart.LineItem item) {
    return Domain.LineItem.newBuilder()
        .setProductId(item.getProductId())
        .setName(item.getName())
        .setQuantity(item.getQuantity())
        .build();
  }
}
// #content
