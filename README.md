Welcome to _SmartColors_, a improved color kernel allowing:

* Untrusted asset servers serving SPV proofs
* Atomic swaps

# Compile

`mvn package`

# Try things out

```
cd tools
# Create wallet from seed and scan
bin/colortool --mnemonic "click uniform area entire lamp engine sure enjoy all adult pigeon size" scan
# Dump existing wallet, and show deposit address
bin/colortool dump
# San existing wallet
bin/colortool scan
```

Asset definitions are in `oil.json` and `gold.json` in the `tools` module.