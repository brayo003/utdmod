# Dynamics Verification Laboratory

This workspace contains a standalone Java simulator that reproduces the field dynamics extracted from the Minecraft implementation without depending on Minecraft itself.

## Build and run

```bash
gradle run
```

The simulator writes:
- CSV metrics to [simulation_lab/dynamics_audit/RESULTS.csv](dynamics_audit/RESULTS.csv)
- markdown audit documents to [simulation_lab/dynamics_audit](dynamics_audit)
