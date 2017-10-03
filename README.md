# Wikipedia graph

Parse a wikipedia dump into different formats containing all links between pages. The final format consists of a binary file and a sqlite3 database that can be combined to do fast breadth first searches to find the shortest paths between pages. I also created a telegram bot [wiki-search-bot](https://github.com/jhnj/wiki-search-bot) to make usage more practical.

The project is built using functional Scala with [fs2](https://github.com/functional-streams-for-scala/fs2) and [cats](https://github.com/typelevel/cats).
Database access is done using simple JDBC combined with fs2 as [doobie](https://github.com/tpolecat/doobie) didn't support `fs2 1.10.0` at the time.

## Usage
Requirements to run:
  - `Scala 2.12.3` + `sbt 0.13.16`
  - `sqlite3`
  - XML dump of all wikipedia articles `enwiki-latest-pages-articles.xml.bz2` which can be downloaded from <https://dumps.wikimedia.org/enwiki/latest/>
  - Simple english wikipedia can be used for trying it out `simplewiki-latest-pages-articles.xml.bz2`, download here <https://dumps.wikimedia.org/simplewiki/latest/>

Download the XML file and move it to the data folder in the project. Then open the sbt console by running `sbt`. To process the dump and go through all intermediate stages run `run pipeline`. On my laptop this process takes about 6 minutes for the Simple English wikipedia dump.

To then find the shortest path between two articles type `run search` in the sbt console. You will then be prompted for two article titles, if both articles exist the shortest path between them will be printed.

### Deploy
Parsing the real wikipedia dump took ~8 hours on an **AWS ec2 r3.large** instance so you probably don't want to do it on your own computer. To get an executable jar run `sbt assembly`.


## Files
The final files used in the search are: `graph.bin` and `index.db`  
#### graph.bin
A binary file where all the articles and their outbound links exist in the following format:  

|Amount of links|Number of article|Offset of link1|Offset of link2|...|
| --- | --- | --- | --- | --- |

Where all fields are 32bit integers

#### index.db
A sqlite3 database containing one table `pages` with the titles of all articles and their offsets in `graph.bin`

