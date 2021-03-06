# TSOpen

TSOpen is a flow-, path- and context-sensitive tool to detect logic bombs in Android applicatons. This is an open implementation of TriggerScope made thanks to the details given in the 2016 Security and Privacy paper by Fratantonio & al.

TSOpen has been developped over the [Soot framework](https://github.com/Sable/soot) which is useful in analyzing statically Java programs thanks to its internal simplified representation of Java bytecode (Jimple).
Since TSOpen is made to analyze Android APKs and such applications have an atypical form (with communicating components), it is difficult to model correctly the flow of information. Therefore the modelling part rely on [Flowdroid](https://github.com/secure-software-engineering/FlowDroid) which is a tool to detect data leaks in Android APKs but can also bu used as a library.

## Getting Started

### Downloading the tool

I do not provide any pre-built JAR as a release yet. Therefore one has to do the following to get the tool : 

<pre>
git clone https://github.com/JordanSamhi/TSOpen.git
</pre>

### Installing the tool

To install the tool, one just has to go into cloned repository and run these maven commands :

<pre>
cd TSOpen
mvn clean install:install-file -Dfile=libs/soot-infoflow-android-classes.jar -DgroupId=de.tud.sse -DartifactId=soot-infoflow-android -Dversion=2.7.1 -Dpackaging=jar
mvn clean install:install-file -Dfile=libs/soot-infoflow-classes.jar -DgroupId=de.tud.sse -DartifactId=soot-infoflow -Dversion=2.7.1 -Dpackaging=jar
mvn clean install:install-file -Dfile=libs/sootclasses-trunk.jar -DgroupId=ca.mcgill.sable -DartifactId=soot -Dversion=3.3.0 -Dpackaging=jar
mvn clean install
</pre>

The built JAR will be in "target" folder with the following name : 
* TSOpen-X.Y-jar-with-dependencies.jar
Where X.Y is the current version of the tool.

### Using the tool

To run the tool, simply issue this command : 

<pre>
java -jar TSOpen/target/TSOpen-X.Y-jar-with-dependencies.jar <i>options</i>
</pre>

Two options are currently required : 

```
java -jar TSOpen/target/TSOpen-X.Y-jar-with-dependencies.jar -f <APK file> -p <path/to/android/platforms>
```

Indeed, one has to provide a file to analyze and the path to the android platforms folder (in Android SDK folder).

Additional options : 

* ```-e``` : Take exceptions into account during full path predicate recovery.
* ```-t <timeout>``` : Set a timeout in minutes for the tool (60 mins by default).
Indeed, the tool faces NP-complete problems, therefore for some apps it can run indefinitely, that is why a timeout is useful in some cases.
* ```-q``` : Quiet mode, do not display information messages.
* ```-o <file_name>``` : Set an input file for saving results.
* ```-c <call_graph_algorithm>``` : Set the call-graph construction algorithm used (SPARK, CHA, RTA, VTA)
* ```-r``` : Display raw results in stdout

Results are in this form in the file for an APK :

```sha256; pkg_name; count_of_triggers; elapsed_time; has_suspicious_trigger; has_suspicious_trigger_after_control_dependency; has_suspicious_trigger_after_post_filters; dex_size; count_of_classes; count_of_if; if_depth; count_of_objects```

Triggers are represented in this form : 

```%if_stmt, class, methodContainingLogicBomb, sensitiveMethod, componentContainingLogicBomb, sizeOfFullPathPredicate, isMethodContainingLogicBombReachable, guardedBlocksDensity, predicate_0:...:predicate_n```

Theses features can be used to compute some data and statistics with a script.

## Built With

* [Maven](https://maven.apache.org/) - Dependency Management

## Authors

* **[Jordan Samhi](https://github.com/JordanSamhi)**

## Publication

If one wants to know more about the implementation details please check the [related research paper](https://wkr.io/publication/oakland-2016-triggerscope.pdf).

## License

This project is licensed under the LGPLv2.1 License - see the [LICENSE](LICENSE) file for details
