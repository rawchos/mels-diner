# Mel's Diner

> Fast foods bring good moods. -Mel

A kitchen simulator that ingests orders from a sample json order file and
places them on their specified shelves (space permitting) for courier delivery
at random intervals.

## Installation

This application uses [leiningen](https://leiningen.org/) for dependency management.
Clone the repo and then run `lein deps` to download dependencies. You can also run
the application with `lein run` or get a repl going with `lein repl`.

## Usage

Run from the command line passing in optional orders file.

```sh
# Run with default resources/orders.json file
lein run

# Run with alternate orders json file
lein run resources/small-orders.json
```

Run tests
```sh
lein test


lein test mels-diner.kitchen-test

Ran 12 tests containing 35 assertions.
0 failures, 0 errors.
```

## Configuration

A default [configuration](resources/config.edn) file is used to setup order ingestion
rate and overall kitchen shelf capacity.

## Strategies

### Placing an order

When placing an order, we first check to see if there's available room on the expected shelf based on the `:temp` of an order. If there's no capacity to add the order to that shelf, we then check to see if there's capacity available on the overflow shelf. If there's no capacity on the overflow shelf, we check to see if we can move an order from the overflow shelf to its expected shelf and place the order on the overflow shelf. If we can't move an order from the overflow shelf, we drop an order from overflow and add the order to the overflow shelf. The dropped order is no longer available for delivery.

### Moving an order from overflow

When moving an order from overflow to its expected shelf, we start checking from the end of the list. Theoretically, these should be the oldest orders and possibly the best chance of being picked up from their shelves soon. When checking the orders in overflow for shelf capacity, we also remember which shelves we've already checked and won't check any other orders for that shelf.

### Dropping an order from overflow

If we can't move an order from overflow due to capacity constraints, we need to drop an order from the overflow orders. We drop the last order because this is theoretically the oldest order already and we want to give the newer orders a chance of being picked up.

### Delivering an order

When delivering an order, we allow the delivery if it's on its expected shelf or on the overflow shelf.

## Logging

We log most activities around receiving and delivering orders and there's a watcher on the `kitchen-status` agent that notifies of changes and prints the current status. Due to the nature of the application, not all logging statements will line up with the change notification updates. I may look into tracking changes inside the kitchen status agent so they'll display along with the change notification but I'm not sure if I'll get the chance to do that.

## Side Note

Close to the end of development on this, I had the thought that maybe I should change how I'm storing orders on shelves. Rather than storing the orders in a collection, like so:

```clojure
{:shelves {:hot {:capacity 10
                 :orders ({:id "order 1" :temp "hot"})}}}
```

I could just `assoc` them by `:id` like so:

```clojure
{:shelves {:hot {:capacity 10
                 :orders {"order 1" {:id "order 1" :temp "hot"}}}}}
```

This would make it a little easier for me to find orders (either on their expected shelf or on the overflow shelf) and would be a little easier to move them around between shelves. It would change up how I'd need to check for capacity but it's certainly doable. The larger change would be how I make determinations around which overflow orders to move to their temperature shelves and how to decide which overflow order to drop when the need arises. I ultimately decided not to follow through with that change because I was too far into holding the orders in collections. I may expirement with making that change on another branch if I have some spare time because it's interesting to me.