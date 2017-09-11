# Wikipedia graph

Parse a wikipedia dump into different formats containing all links between pages. The final format consists of a binary file and a sqlite3 database that can be combined to do fast breadth first searches to find the shortest paths between pages. The project is still a WIP and I have thus far only tested it with the smaller "Simple English" wikipedia dump.

The project is built using functional Scala with [fs2](https://github.com/functional-streams-for-scala/fs2) and [cats](https://github.com/typelevel/cats).

## Usage
Requirements to run:
  - `Scala 2.12.3` + `sbt 0.13.16`
  - `sqlite3`
  - XML dump of all wikipedia articles `simplewiki-latest-pages-articles.xml.bz2` which can be downloaded from <https://dumps.wikimedia.org/simplewiki/latest/>

Unzip the XML file and move it to the data folder in the project. Then open the sbt console by running `sbt`. To process the dump and go through all intermediate stages run `run pipeline`. On my laptop this process takes about 6 minutes for the Simple English wikipedia dump.

To then find the shortest path between two articles type `run search` in the sbt console. You will then be prompted for two article titles, if both articles exist the shortest path between them will be printed.

## Files
The final files used in the search are: `graph.bin` and `index.db`  
#### graph.bin
A binary file where all the articles and their outbound links exist in the following format:  

|Amount of links|Number of article|Offset of link1|Offset of link2|...|
| --- | --- | --- | --- | --- |

Where all fields are 32bit integers

#### index.db
A sqlite3 database containing one table `pages` with the titles of all articles and their offsets in `graph.bin`

## ToDo
 - Read xml from StdIn to be able to pipe the file directly from `bzip2` to the parser as the real wiki dump is rather big uncompressed
 - Test performance and process real wiki dump (AWS?)
 - Improvements to searching
   * Currently crashes if article not found
   * Currently case sensitive (add nocase index to db?)
   * Non cli, maybe telegram bot?

