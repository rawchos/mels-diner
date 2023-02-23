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

## Configuration

A default [configuration](resources/config.edn) file is used to setup order ingestion
rate and overall kitchen shelf capacity.

## Examples

...
