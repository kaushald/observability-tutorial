# 000: Debugging without traces

Three services, three log streams, and nothing connecting them.

## Run it

```
cd advanced/000-baseline
./run.sh
```

No API key is needed. Open the URL it prints and place a few orders from each restaurant.

## The problem

Orders from one restaurant fail. Using only the log lines in your terminal, work out:

1. Which service is at fault?
2. Why does the order fail?

Give yourself five minutes, then move to `001-auto`.
