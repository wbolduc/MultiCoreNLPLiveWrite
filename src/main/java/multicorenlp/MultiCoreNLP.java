/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package multicorenlp;

import edu.stanford.nlp.ling.AnnotationLookup;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.Tree;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FilenameUtils;

/**
 *
 * @author wbolduc
 */
public class MultiCoreNLP implements Callable {
    private static int threadCount = 1;// = 8;
    private static int chunkSize = 200;
    private static int updatePeriod = 1000; //ms
    
    private static String inputFile;
    private static String outputFile;
    
    private static final int reportInterval = 50;
    private final StanfordCoreNLP pipeline;
    private final List<Tweet> tweets;
    
    private final AtomicInteger tweetsParsed;
    
    
    static private final Collection<GrammaticalRelation> SUBJECTS = new HashSet(Arrays.asList(  GrammaticalRelation.valueOf("nsubj"),
                                                                                                GrammaticalRelation.valueOf("nsubjpass"),
                                                                                                GrammaticalRelation.valueOf("csubj"),
                                                                                                GrammaticalRelation.valueOf("csubjpass")));
    static private final Collection<GrammaticalRelation> OBJECTS = new HashSet(Arrays.asList(   GrammaticalRelation.valueOf("dobj"),
                                                                                                GrammaticalRelation.valueOf("iobj")));
    static private final Collection<GrammaticalRelation> COORDINATIONS = new HashSet(Arrays.asList( GrammaticalRelation.valueOf("conj"),
                                                                                                    GrammaticalRelation.valueOf("cc"),
                                                                                                    GrammaticalRelation.valueOf("punct")));
    
    static private final Set<GrammaticalRelation> REJECT = new HashSet(COORDINATIONS);
    static private final Set<GrammaticalRelation> KEEP = new HashSet(Arrays.asList( GrammaticalRelation.valueOf("neg"),
                                                                                    GrammaticalRelation.valueOf("amod"),
                                                                                    GrammaticalRelation.valueOf("det"),
                                                                                    GrammaticalRelation.valueOf("dep"),
                                                                                    GrammaticalRelation.valueOf("nummod"),
                                                                                    GrammaticalRelation.valueOf("nmod"),
                                                                                    GrammaticalRelation.valueOf("xcomp"),
                                                                                    GrammaticalRelation.valueOf("appos")));
    static private final GrammaticalRelation NEGATION = GrammaticalRelation.valueOf("neg");
    
    static private final Comparator sortIndexedWords = new Comparator(){
            @Override
            public int compare(Object o1, Object o2) {
                IndexedWord a = (IndexedWord) o1;
                IndexedWord b = (IndexedWord) o2;
                return a.index() - b.index();
            }
        };
    
    
    /*
    private final Collection<GrammaticalRelation> SUBJECTS = new HashSet(Arrays.asList(EnglishGrammaticalRelations.NOMINAL_SUBJECT,
                                                                                       EnglishGrammaticalRelations.NOMINAL_PASSIVE_SUBJECT,
                                                                                       EnglishGrammaticalRelations.CLAUSAL_SUBJECT,
                                                                                       EnglishGrammaticalRelations.CLAUSAL_PASSIVE_SUBJECT));
    
    private final Collection<GrammaticalRelation> OBJECTS = new HashSet(Arrays.asList( EnglishGrammaticalRelations.DIRECT_OBJECT,
                                                                                       EnglishGrammaticalRelations.INDIRECT_OBJECT));
    
    private final Collection<GrammaticalRelation> COORDINATIONS = new HashSet(Arrays.asList(EnglishGrammaticalRelations.CONJUNCT,
                                                                                            EnglishGrammaticalRelations.COORDINATION,
                                                                                            EnglishGrammaticalRelations.PUNCTUATION));
    */
    
    MultiCoreNLP(StanfordCoreNLP pipeline, List<Tweet> tweets, AtomicInteger counter)
    {
        this.pipeline = pipeline;
        this.tweets = new ArrayList<>(tweets);
        this.tweetsParsed = counter;
    }
    
    @Override
    public List<Tweet> call() {
        System.out.println("Start worker " + Thread.currentThread().getId());
        long totalTimeMS = System.nanoTime();
        extract();
        totalTimeMS = (System.nanoTime() - totalTimeMS)/1000000;
        System.out.println("Chunk finished in " + totalTimeMS + " at " + totalTimeMS/chunkSize + "ms/tweet");
                //getMessegeWithElapsed("Worker " + Thread.currentThread().getId() + " task finished in ", threadStartTime));
        return tweets;
    }   
    
    public void extract()
    {
        int i = 0;
        for(Tweet tweet : tweets)
        {
            //annotate
            pipeline.annotate(tweet.text);

//            HashMap <Pair<Integer, Integer>, String> refMap = getDocRefMap(tweet.text);
            
            //get svos in this tweet
            List<SVO> sentSVOs = new ArrayList<>();
            for(CoreSentence sent : tweet.text.sentences())
            {
                sentSVOs.addAll(extractSVOsLocalSent(sent));
            }
            //save SVOs to tweet
            tweet.setSvos(sentSVOs);
            
            if((++i) == reportInterval)
            {
                tweetsParsed.addAndGet(reportInterval);
                i = 0;
            }
        }
    }
    
    public void treePrint(Tree tree, String indent)
    {
        System.out.println(indent + tree.value() + " " + tree.score());
        
        for(Tree child : tree.children())
            treePrint(child, indent + " ");
    }
    
    public ArrayList<SVO> extractSVOs(CoreSentence sent)
    {
        ArrayList<SVO> svos = new ArrayList<>();
        
        SemanticGraph semTree = sent.dependencyParse();
        

        //LabeledScoredTreeNode conTree = (LabeledScoredTreeNode) sent.constituencyParse();
        //LabeledScoredTreeNode sentTree = (LabeledScoredTreeNode) sent.sentimentTree();
       
        
        
        
        int sentiment = RNNCoreAnnotations.getPredictedClass(sent.sentimentTree());

        for (IndexedWord word : semTree.vertexSet())
        {
            if(word.tag().startsWith("VB"))
            {
                Set<IndexedWord> verbSubs = semTree.getChildrenWithRelns(word, SUBJECTS);
                Set<IndexedWord> verbObjs = semTree.getChildrenWithRelns(word, OBJECTS);
                
                //int wordScore = RNNCoreAnnotations.getPredictedClass(word.backingLabel());
                
                //boolean negated = semTree.isNegatedVertex(word);
                for(IndexedWord verbSub : verbSubs)
                    for(IndexedWord verbObj : verbObjs)
                    {
                        //if (verbObj.lemma().equals("what"))
                        //    System.out.println("...\t" + sent.text() + " " + verbSub.originalText() + "/" + verbSub.lemma() + " " + word.originalText() + "/" + word.lemma() + " " + verbObj.originalText() + "/" + verbObj.lemma());
                        svos.add(new SVO(verbSub.lemma(), semTree.isNegatedVertex(verbSub), word.lemma(), semTree.isNegatedVertex(word), verbObj.lemma(), semTree.isNegatedVertex(verbObj), sentiment));
                    }
            }
        }
        return svos;
    }

    public ArrayList<SVO> extractSVOsLocalSent(CoreSentence sent)
    {        
        ArrayList<SVO> svos = new ArrayList<>();
        
        SemanticGraph semTree = sent.dependencyParse();
        
        //SemanticGraph plusplus = sent.coreMap().get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
        
        //LabeledScoredTreeNode conTree = (LabeledScoredTreeNode) sent.constituencyParse();
        //LabeledScoredTreeNode sentTree = (LabeledScoredTreeNode) sent.sentimentTree();
        
        /*
        List<LabeledScoredTreeNode> children = sentTree.getLeaves();
        children.forEach(child -> {
            CoreLabel childLabel = (CoreLabel) child.label();
            SimpleMatrix predictions = RNNCoreAnnotations.getPredictions(child);
            
            System.out.println(childLabel.originalText() + " : " + predictions.toString());
                });
        sentTree.indentedListPrint();
        */
        
        
        //int sentiment = RNNCoreAnnotations.getPredictedClass(sent.sentimentTree());

        for (IndexedWord word : semTree.vertexSet())
        {
            if(word.tag().startsWith("VB"))
            {
                Set<IndexedWord> verbSubs = semTree.getChildrenWithRelns(word, SUBJECTS);
                Set<IndexedWord> verbObjs = semTree.getChildrenWithRelns(word, OBJECTS);
                
                //int wordScore = RNNCoreAnnotations.getPredictedClass(word.backingLabel());
                
                //boolean negated = semTree.isNegatedVertex(word);
                if(verbSubs.size()>0 && verbObjs.size() > 0)
                {   
                    for(IndexedWord verbSub : verbSubs)
                        for(IndexedWord verbObj : verbObjs)
                        {
                            List<IndexedWord> blackSent = blackSubSentence(semTree, verbSub, word, verbObj);
                            List<IndexedWord> whiteSent = whiteSubSentence(semTree, verbSub, word, verbObj);

                            ArrayList<String> debug = new ArrayList<>();
                            semTree.typedDependencies().forEach(dep -> debug.add(dep.gov() + " " + dep.reln().getLongName() + " " + dep.dep()));
                            
                            //svos.add(new SVO(verbSub.lemma(), word.lemma(), verbObj.lemma(), 0.0));// old
                            svos.add(new SVOWithSubs(verbSub.lemma(), negated(semTree,verbSub), word.lemma(), negated(semTree,word), verbObj.lemma(), negated(semTree,verbObj), 0.0, remakeSentence(whiteSent), remakeSentence(blackSent)));   //not sure which subsentencer works better, save both
                        }
                }
            }
        }
        return svos;
    }
    
    boolean negated(SemanticGraph semTree, IndexedWord word)
    {
        int negations = semTree.getChildrenWithReln(word, NEGATION).size();
        if(negations % 2 == 0)
            return false;
        return true;
    }
    
    private String remakeSentence(List<IndexedWord> words)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(words.get(0).originalText());
        for(int i = 1; i < words.size(); i++)
        {
            sb.append(" ");
            sb.append(words.get(i).originalText());
        }
        return sb.toString();
    }
    /*
    private List<IndexedWord> whiteSubSentence(SemanticGraph semTree, IndexedWord subject, IndexedWord verb, IndexedWord object)
    {
        TreeSet<IndexedWord> subSent = whiteListConnections(semTree, subject);
        subSent.addAll(whiteListConnections(semTree, verb));
        subSent.addAll(whiteListConnections(semTree, object));
        
        return new ArrayList<>(subSent);
    }
    
    private TreeSet<IndexedWord> whiteListConnections(SemanticGraph semTree, IndexedWord noun)
    {
        Set<GrammaticalRelation> KEEP = new HashSet(Arrays.asList(  GrammaticalRelation.valueOf("neg"),
                                                                    GrammaticalRelation.valueOf("amod"),
                                                                    GrammaticalRelation.valueOf("det"),
                                                                    GrammaticalRelation.valueOf("dep"),
                                                                    GrammaticalRelation.valueOf("nummod"),
                                                                    GrammaticalRelation.valueOf("nmod"),
                                                                    GrammaticalRelation.valueOf("xcomp"),
                                                                    GrammaticalRelation.valueOf("appos")));
        
        Set<IndexedWord> children = semTree.getChildrenWithRelns(noun, KEEP);
        
        TreeSet<IndexedWord> result = new TreeSet<>(new Comparator(){
            @Override
            public int compare(Object o1, Object o2) {
                IndexedWord a = (IndexedWord) o1;
                IndexedWord b = (IndexedWord) o2;
                return a.index() - b.index();
            }
        });
        
        children.forEach(child -> result.addAll(whiteListConnections(semTree, child)));
        result.add(noun);
        
        return result;
    }
    */
    private List<IndexedWord> whiteSubSentence(SemanticGraph semTree, IndexedWord subject, IndexedWord verb, IndexedWord object)
    {
        List<IndexedWord> unCheckedWords = new ArrayList<>();
        TreeSet<IndexedWord> checkedWords = new TreeSet<>(sortIndexedWords);
        
        //build initial list
        unCheckedWords.add(subject);
        unCheckedWords.add(object);     //This needs to be in this order since verb points to both subject and object
        unCheckedWords.add(verb);
          
        while(unCheckedWords.size()>0)
        {
            IndexedWord word = unCheckedWords.remove(0);            
            if(!checkedWords.contains(word)) //if this is not a word we've seen already
            {
                unCheckedWords.addAll(semTree.getChildrenWithRelns(word, KEEP));
                checkedWords.add(word);
            }
        }
        
        return new ArrayList<>(checkedWords);
    }

    
    private List<IndexedWord> blackSubSentence(SemanticGraph semTree, IndexedWord subject, IndexedWord verb, IndexedWord object)
    {
        List<IndexedWord> unCheckedWords = new ArrayList<>();
        TreeSet<IndexedWord> checkedWords = new TreeSet<>(sortIndexedWords);
        
        //build initial list
        unCheckedWords.add(subject);
        unCheckedWords.add(object);     //This needs to be in this order since verb points to both subject and object
        unCheckedWords.add(verb);

        while(unCheckedWords.size()>0)
        {
            IndexedWord word = unCheckedWords.remove(0);            
            if(!checkedWords.contains(word)) //if this is not a word we've seen already
            {
                List<IndexedWord> wordChildren = semTree.getChildList(word);
                if (wordChildren != null)
                    wordChildren.forEach(child -> {
                        GrammaticalRelation rel = semTree.getEdge(word, child).getRelation();
                        if(!REJECT.contains(rel) && !REJECT.contains(rel.getParent()))  //this is basically a hack, the grammatical relation has a child field but cannot be accessed because it's private, so I need to check the other way even though it;s very ugly
                            unCheckedWords.add(child);
                    });
         
                checkedWords.add(word);
            }
        }
        
        return new ArrayList<>(checkedWords);
    }
    
    
    private void printDeps(SemanticGraph deps)
    {
        deps.typedDependencies().forEach(node -> System.out.println(node.toString()));
    }    
    
    public static void main(String[] args){
        readArgs(args);
        
        
        System.out.println("Loading from " + inputFile);
        System.out.println("Writing to   " + outputFile);
                
        // set up pipeline properties
        Properties props = new Properties();
        // set the list of annotators to run
        props.setProperty("annotators", "tokenize, ssplit, parse, lemma, ner, sentiment");//, coref"); 
        // set a property for an annotator, in this case the coref annotator is being set to use the neural algorithm
        //props.setProperty("coref.algorithm", "neural");
        //props.setProperty("parse.model", "englishPCFG.caseless.ser.gz");
        props.setProperty("depparse.language", "english");
        
        // build pipeline
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        
        // load tweets
        List<Tweet> tweets = null;
        try {
            tweets = loadAllCSVTweets(inputFile);
        } catch (IOException ex) {
            System.out.println("Could not load file.");
            return;
        }
        System.out.println("Loaded " + Integer.toString(tweets.size()) + " Tweets from " + inputFile);
        
        //make a csv writer
        CSVTweetWriter writer;
        try {
            writer = new CSVTweetWriter(new File(outputFile));
        } catch (IOException ex) {
            System.out.println("Could not open out file");
            return;
        }
        
        // split tweets
        int tweetCount = tweets.size();
        
        // create progress counter
        AtomicInteger tweetsParsed = new AtomicInteger(0);
        Thread counter = new Thread(new ConcurrentCounter("Tweets parsed : ", tweetsParsed, updatePeriod));
        
        //create pool
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        //create runnables and add to pool
        ArrayList<Future<List<Tweet>>> returns = new ArrayList<>();
        for (int i = 0; i < tweetCount; i += chunkSize)
            returns.add(executor.submit(new MultiCoreNLP(pipeline, new ArrayList<>(tweets.subList(i, Math.min(i+chunkSize, tweetCount))), tweetsParsed)));
        executor.shutdown();
        
        //"free memory" (activate the garbage collector)
        tweets = null;
        
        // Start counter
        if(updatePeriod > 0)
            counter.start();
        
        //collectReturns
        int returnsWritten = 0;
        while(returns.size() > 0)
        {
            Future<List<Tweet>> toPrint = returns.remove(0);
            try {
                for(Tweet tweet : toPrint.get())
                    writer.writeTweet(tweet);
                System.out.println("Writing " + Integer.toString(returnsWritten));
                returnsWritten++;
            } catch (InterruptedException ex) {
                Logger.getLogger(MultiCoreNLP.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ExecutionException ex) {
                Logger.getLogger(MultiCoreNLP.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        //stop counter
        counter.interrupt();
        //close writer
        writer.close();
        
        System.out.println(pipeline.timingInformation());
    }
    
    private static void readArgs(String[] args)
    {
        
        Options options = new Options();
        options.addOption("h", "help", false, "Displays help messege");
        options.addOption("i", "inputFile",true,"The input csv");
        options.addOption("o", "outputFile", true, "The file to be output");
        options.addOption("c", "cores", true, "Number of cores to use");
        options.addOption("b", "batchSize", true, "The size of the batches of tweets");
        options.addOption("u", "updateFreq", true, "How frequently the program updates you on progress in milliseconds, -1 if no updates");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
            if(cmd.hasOption("h"))
            {
                (new HelpFormatter()).printHelp("MulitCoreNLP", options);
                System.exit(0);
            }

            threadCount = Integer.parseInt(cmd.getOptionValue("c","1"));
            if(threadCount < 1)
            {
                System.out.println("Must have at least 1 thread");
                System.exit(0);
            }
            chunkSize = Integer.parseInt(cmd.getOptionValue("b","200"));
            if(chunkSize < 1)
            {
                System.out.println("Batch size must at least be 1");
                System.exit(0);
            }
            updatePeriod = Integer.parseInt(cmd.getOptionValue("u", "1000"));

            if(cmd.hasOption("i"))
            {
                inputFile = validPath(cmd.getOptionValue("i"),"input");
                if(FilenameUtils.isExtension(inputFile, "csv") == false)
                {
                    System.out.println("Input file must be cvs");
                    System.exit(0);
                }
            }
            else
            {
                System.out.println("Need an input file");
                System.exit(0);
            }
            if(cmd.hasOption("o"))
                outputFile = validPath(cmd.getOptionValue("o"), "output");
            else
            {
                outputFile =   FilenameUtils.getFullPath(inputFile) +
                            FilenameUtils.getBaseName(inputFile) +
                            "_svos.csv";
            }
        } catch (ParseException ex) {
            Logger.getLogger(MultiCoreNLP.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(0);
        }
    }
    
    private static String validPath(String path, String type)
    {
        path = FilenameUtils.normalize(path);
        if (path == null)
        {
            System.out.println("Not a valid " + type + " path");
            System.exit(0);
        }
        return path;
    }
    
    public static ArrayList<Tweet> loadAllCSVTweets(String fileName) throws FileNotFoundException, IOException
    {
        ArrayList<Tweet> tweets = new ArrayList<>();
        
        Reader csvData = new FileReader(fileName);
        Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(csvData);
        for(CSVRecord record : records)
        {
            String text = record.get("text");
            text = removeURLS(text);
            tweets.add(new Tweet(Long.parseLong(record.get("id")), record.get("user_screen_name"), new CoreDocument(text)));
        }
        return tweets;
    }
    
    public static String getMessegeWithElapsed(String message, long startTime) {
        final StringBuilder sb = new StringBuilder();
        long elapsed = (System.nanoTime() - startTime) / 1000000;
        sb.append(message);
        sb.append(" : ");
        sb.append(elapsed);
        sb.append(" ms");
        return sb.toString();
    }
    public static String removeURLS(String text)
    {
        String urlPattern = "(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
        return text.replaceAll(urlPattern, "");
    }
}
/*
    public ArrayList<SVO> extractSVOsFullName(CoreSentence sent, HashMap<Pair<Integer, Integer>, String> docRef)
    {
        ArrayList<SVO> svos = new ArrayList<>();
        
        SemanticGraph semTree = sent.dependencyParse();
        
        for (IndexedWord word : semTree.vertexSet())
        {
            if(word.tag().startsWith("VB"))
            {
                Set<IndexedWord> verbSubs = semTree.getChildrenWithRelns(word, SUBJECTS);
                Set<IndexedWord> verbObjs = semTree.getChildrenWithRelns(word, OBJECTS);
                
                boolean negated = semTree.isNegatedVertex(word);
                String svoSub;
                String svoObj;
                for(IndexedWord verbSub : verbSubs)
                {
                    svoSub = docRef.get(new Pair(verbSub.sentIndex(), verbSub.index()));
                    if(svoSub == null)
                            svoSub = verbSub.lemma();
                    
                    for(IndexedWord verbObj : verbObjs)
                    {
                        svoObj = docRef.get(new Pair(verbObj.sentIndex(), verbObj.index()));
                        if(svoObj == null)
                            svoObj = verbObj.lemma();
                        svos.add(new SVO(svoSub, word.lemma(), svoObj, negated));
                    }
                }
            }
        }
        return svos;
    }
    
    public static String getNamedEntityInMention(CoreDocument doc, CorefChain.CorefMention mention)
    {
        CoreSentence sent = doc.sentences().get(mention.sentNum-1);
        
        int startOfMention = mention.startIndex;
        int endOfMention = mention.endIndex;
        for(CoreEntityMention entMention : sent.entityMentions())
        {
            List<CoreLabel> mentionTokens = entMention.tokens();
            int entStart = mentionTokens.get(0).index();
            int entEnd = mentionTokens.get(mentionTokens.size()-1).index();
            if (startOfMention <= entStart && entEnd <= endOfMention)
                return entMention.text();
        }
        return null;
    }
    
    public static HashMap<Pair<Integer, Integer>, String> getDocRefMap(CoreDocument doc)
    {
        HashMap<Pair<Integer, Integer>, String> refMap = new HashMap<>();
        
        //get chains
        Collection<CorefChain> corefs = doc.corefChains().values();
        //get sentences
        List<CoreSentence> sents = doc.sentences();

        //go through chains and add mappings
        for(CorefChain coref: corefs)
        {
            List<CorefChain.CorefMention> mentions = coref.getMentionsInTextualOrder();
            //get "who" this mention is
            String repMention = coref.getRepresentativeMention().mentionSpan;
            
            for(CorefChain.CorefMention mention : mentions)
            {
                if (mention.equals(repMention)) //don't annotate the core mention with itself
                    continue;
                
                int sentNumber = mention.sentNum;
                int wordNumber = mention.headIndex;

                refMap.put(new Pair(sentNumber, wordNumber), repMention);
            }
        }   
        return refMap;
    }
    /*
    public static List<SVO> extractSVOs(SemanticGraph depRels)
    {
        List<SVO> svos = new ArrayList<>();
        
        Collection<TypedDependency> typedDeps = depRels.typedDependencies();
        typedDeps.forEach(typedDep ->{    
            if(typedDep.reln().getShortName().startsWith("nsub"))   //this dep is a subject
            {
                IndexedWord verb = typedDep.gov();
                
                Boolean negated = false;//depRels.isNegatedVertex(verb);    //doesnt seem to work
                for(GrammaticalRelation gr : depRels.childRelns(verb))
                    if (gr.getShortName().equals("neg"))
                        if(negated == false)
                            negated = true;
                        else
                            negated = false;
                
                for (TypedDependency obj : typedDeps)
                {
                    String relName = obj.reln().getShortName();
                    if (obj.gov().equals(verb))
                    {
                        if(relName.equals("dobj"))//object sharing the same verb
                            svos.add(new SVO(typedDep.dep().lemma(), verb.lemma(), obj.dep().lemma(), negated));
                        else if (relName.startsWith("nmod"))
                            svos.add(new SVO(typedDep.dep().lemma(), verb.lemma(), obj.dep().lemma(), negated, obj.reln().toString()));
                    }
                }
            }
        });
        return svos;
    }
    */
    /*
    public static List<SVO> extractSVOs(SemanticGraph depRels, HashMap<CoreLabel, CorefChain.CorefMention> refMap)
    {
        List<SVO> svos = new ArrayList<>();
        
        Collection<TypedDependency> typedDeps = depRels.typedDependencies();
        typedDeps.forEach(typedDep ->{    
            if(typedDep.reln().getShortName().startsWith("nsub"))   //this dep is a subject
            {
                IndexedWord verb = typedDep.gov();
                
                Boolean negated = false;//depRels.isNegatedVertex(verb);    //doesnt seem to work
                for(GrammaticalRelation gr : depRels.childRelns(verb))
                    if (gr.getShortName().equals("neg"))
                        if(negated == false)
                            negated = true;
                        else
                            negated = false;
                
                for (TypedDependency obj : typedDeps)
                {
                    String relName = obj.reln().getShortName();
                    if (obj.gov().equals(verb))
                    {
                        if(relName.equals("dobj") || relName.startsWith("nmod"))//object sharing the same verb
                        {
                            //check if these subjects and objects are referenced
                            CorefChain.CorefMention subjectCoref = refMap.get(typedDep.dep().backingLabel());
                            CorefChain.CorefMention objectCoref = refMap.get(obj.dep().backingLabel());
                            
                            String subject;
                            if (subjectCoref == null)
                                subject = typedDep.dep().lemma();
                            else
                                subject = subjectCoref.mentionSpan;
                            
                            String object;
                            if (objectCoref == null)
                                object = obj.dep().lemma();
                            else
                                object = objectCoref.mentionSpan; //needs to be a lemma
                            SVO temp;
                            if (objectCoref != null || subjectCoref != null)
                                temp = new SVO(subject, verb.lemma(), object, negated, obj.reln().toString() + " corefed");
                            else
                                temp = new SVO(subject, verb.lemma(), object, negated, obj.reln().toString());
                            svos.add(temp);
                            System.out.println(temp.toString());
                        }
                    }
                }
            }
        });
        return svos;
    }
    */