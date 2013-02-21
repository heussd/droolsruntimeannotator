# UIMA DroolsRuntimeAnnotator

* Author:    Timm Heuss (Timm.Heuss AT web.de)
* GitHub:    <https://github.com/heussd/droolsruntimeannotator>

---

## What does it do?
This Java software is an Analysis Engine that integrates the JBoss Drools Rule Engine into an UIMA pipeline. This allows you to process and manipulate Feature Structures of a given CAS in *"the Drools way"* - with declarative Drools rules stored separatly from your application.

## Features

* delete / change / create Feature Structures with native Drools capacities (thanks to a listener)
* access Feature Structures and attributes with native Drools capacities (thanks to the Drools nature)
* configure the DroolsRuntimeAnnotator with native UIMA capacities

## Demo
The code comes with a simple JUnit test case consisting of a small, DKPro-based UIMA pipeline. It demonstrates a straight forward scenario involving two Drools rules.


## What do you think?


Feel free to mail the author your feedback: Timm.Heuss AT web.de.
