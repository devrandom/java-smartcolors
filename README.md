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

Asset definitions are in `oil.smartcolor` and `gold.smartcolor` in the `tools` module.

# Issuance

After scanning a wallet that has a Bitcoin balance:

```
bin/colortool issue NAME 1000000
```

This will create two files: `NAME.smartcolor` and `NAME.scdef`.  The `smartcolor` JSON file is a bare-bones asset definition with
just one item of metadata - the name.  You can add other metadata, such as divisibility, as needed.  The `scdef` file
contains the consensus definition and can be imported into the SmartColors server.

## Adding to server

```
# use --testnet for TestNet3
./smartassetctl addcolordef NAME.scdef
./smartassetctl scan
```

## Adding to clients

The augmented smartcolor JSON file should be added to the resources directory in this tools module in ```src/main/resources``` and in the
SmartWallet project in ```wallet/assets/smartassets```.

The JSON file can also be put up on a URL to let users manually add.
