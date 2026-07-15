# Hybrid Homeostatic Relaxation Report

## Mathematical implementation
- Effective relaxation coefficient: $\lambda_{effective} = (\lambda_{min} + \lambda_{gain} \sigma(T)) e^{-\beta M} / (1 + \gamma D)$
- Sigmoid response: $\sigma(T) = 1 / (1 + e^{-k(T - T_c)})$
- Local tension update: $dT/dt = Injection - \lambda_{effective} T$

## Parameters used
- lambda_min = 0.0006
- lambda_gain = 0.0024
- k = 15.0
- T_c = 1.0
- beta = 0.25
- gamma = 0.30

## single_impulse
- Baseline peak activation: 1.9191
- Hybrid peak activation: 1.9191
- Baseline final memory: 1649.2288
- Hybrid final memory: 1649.2288
- Baseline final depth: 2053.9689
- Hybrid final depth: 2053.9689

## continuous
- Baseline peak activation: 2.1914
- Hybrid peak activation: 2.1914
- Baseline final memory: 1671.4546
- Hybrid final memory: 1671.4546
- Baseline final depth: 2067.2614
- Hybrid final depth: 2067.2614

## recovery
- Baseline peak activation: 1.9191
- Hybrid peak activation: 1.9191
- Baseline final memory: 1649.2288
- Hybrid final memory: 1649.2288
- Baseline final depth: 2053.9689
- Hybrid final depth: 2053.9689

## two_impulses
- Baseline peak activation: 1.9333
- Hybrid peak activation: 1.9333
- Baseline final memory: 1655.7881
- Hybrid final memory: 1655.7881
- Baseline final depth: 2059.0403
- Hybrid final depth: 2059.0403

## extreme_abuse
- Baseline peak activation: 2.7923
- Hybrid peak activation: 2.7923
- Baseline final memory: 1737.2869
- Hybrid final memory: 1737.2869
- Baseline final depth: 2109.8767
- Hybrid final depth: 2109.8767

