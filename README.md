# ctakes-ade
cTAKES module for extracting drug adverse events

This module was built for the N2C2 Challenge: 2018 Track 2: Adverse Drug Events and Medication Extraction in EHRs.

To run it, use the main class:
org/apache/ctakes/ade/eval/EvaluateN2C2Relations.java

with arguments pointing to the training and test data:
```
-t
<directory with training .ann files>
-e
<directory with test .ann files>
-x
<directory where intermediate cTAKES processed files can be stored -- needs to be PHI-compliant since it stores full text of notes>
--output-dir
<directory to write output .ann files [default=n2c2]>
```

This will write an approximate micro- and macro-F score but for an official score please use the tool released by the N2C2 Challenge organizers, pointing it at the directory specified above by the --output-dir argument.
