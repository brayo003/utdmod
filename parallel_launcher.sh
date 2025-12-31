#!/usr/bin/env bash
# requires GNU parallel installed
RMIN=-1.5
RMAX=1.8
STEPS=41
TRIALS_PER_R=500
PARTS=8  # number of parallel worker processes; adjust to CPU cores

# create job list: each job runs utd_sweep.py with a part index
for i in $(seq 0 $((PARTS-1))); do
  echo "python3 utd_sweep.py --part $i --parts $PARTS --rmin $RMIN --rmax $RMAX --steps $STEPS --trials $TRIALS_PER_R"
done > jobs.txt

parallel --jobs $PARTS < jobs.txt
# after parallel finishes, combine CSV parts (user script expected to create part files)
