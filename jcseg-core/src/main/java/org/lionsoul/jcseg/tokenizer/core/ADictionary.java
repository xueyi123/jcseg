package org.lionsoul.jcseg.tokenizer.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.CodeSource;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.lionsoul.jcseg.util.StringUtil;


/**
 * Dictionary abstract super class
 * 
 * @author  chenxin<chenxin619315@gmail.com>
 */
public abstract class ADictionary 
{
    /**
     * the default auto load task file name 
    */
    public static final String AL_TODO_FILE = "lex-autoload.todo";
    
    protected JcsegTaskConfig config;
    protected boolean sync;
    
    /**auto load thread */
    private Thread autoloadThread = null;
    
    /**
     * initialize the ADictionary
     * 
     * @param   config
     * @param   sync
    */
    public ADictionary( JcsegTaskConfig config, Boolean sync ) 
    {
        this.sync   = sync.booleanValue();
        this.config = config;
    }
    
    /**
     * load all the words from a specified lexicon file
     * 
     * @param   file
     * @throws  IOException 
     * @throws  FileNotFoundException 
     * @throws  NumberFormatException 
     */
    public void load( File file ) 
            throws NumberFormatException, FileNotFoundException, IOException
    {
        loadWords(config, this, file);
    }
    
    /**
     * load all the words from a specified lexicon path
     * 
     * @param   file
     * @throws  IOException 
     * @throws  FileNotFoundException 
     * @throws  NumberFormatException 
    */
    public void load( String file ) 
            throws NumberFormatException, FileNotFoundException, IOException
    {
        loadWords(config, this, file);
    }
    
    /**
     * load all the words from a specified lexicon input stream
     * 
     * @param   is
     * @throws  IOException 
     * @throws  NumberFormatException 
    */
    public void load( InputStream is ) throws NumberFormatException, IOException
    {
        loadWords(config, this, is);
    }
    
    /**
     * load the all the words form all the files under a specified lexicon directory
     * 
     * @param   lexDir
     * @throws  IOException
     */
    public void loadDirectory( String lexDir ) throws IOException
    {
        File path = new File(lexDir);
        if ( path.exists() == false ) {
            throw new IOException("Lexicon directory ["+lexDir+"] does'n exists.");
        }
        
        /*
         * load all the lexicon file under the lexicon path 
         *     that start with "lex-" and end with ".lex".
         */
        File[] files = path.listFiles(new FilenameFilter(){
            @Override
            public boolean accept(File dir, String name) {
                return (name.startsWith("lex-") && name.endsWith(".lex"));
            }
        });
        
        for ( File file : files ) {
            load(file);
        }
    }
    
    /**
     * load all the words from all the files under the specified class path.
     * 
     * added at 2016/07/12:
     * only in the jar file could the ZipInputStream available
     * add IDE classpath supported here
     * 
     * @param   lexDir
     * @since   1.9.9
     * @throws  IOException
    */
    public void loadClassPath() throws IOException
    {
        Class<?> dClass    = this.getClass();
        CodeSource codeSrc = this.getClass().getProtectionDomain().getCodeSource();
        if ( codeSrc == null ) {
            return;
        }
        
        String codePath = codeSrc.getLocation().getPath();
        if ( codePath.toLowerCase().endsWith(".jar") ) {
            ZipInputStream zip = new ZipInputStream(codeSrc.getLocation().openStream());
            while ( true ) {
                ZipEntry e = zip.getNextEntry();
                if ( e == null ) {
                    break;
                }
                
                String fileName = e.getName();
                if ( fileName.endsWith(".lex") 
                        && fileName.startsWith("lexicon/lex-") ) {
                    load(dClass.getResourceAsStream("/"+fileName));
                }
            }
        } else {
            //now, the classpath is an IDE directory 
            //  like eclipse ./bin or maven ./target/classes/
            loadDirectory(codePath+"/lexicon");
        }
    }
    
    /**
     * start the lexicon autoload thread
    */
    public void startAutoload() 
    {
        if ( autoloadThread != null 
                || config.getLexiconPath() == null ) {
            return;
        }
        
        //create and start the lexicon auto load thread
        autoloadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                String[] paths = config.getLexiconPath();
                AutoLoadFile[] files = new AutoLoadFile[paths.length];
                for ( int i = 0; i < files.length; i++ ) {
                    files[i] = new AutoLoadFile(paths[i] + "/" + AL_TODO_FILE);
                    files[i].setLastUpdateTime(files[i].getFile().lastModified());
                }
                
                while ( true ) {
                    //sleep for some time (seconds)
                    try {
                        Thread.sleep(config.getPollTime() * 1000);
                    } catch (InterruptedException e) {break;}
                    
                    
                    //check the update of all the reload todo files
                    File f          = null;
                    AutoLoadFile af = null;
                    
                    for ( int i = 0; i < files.length; i++ ) {
                        af  = files[i];
                        f   = files[i].getFile();
                        
                        if ( ! f.exists() ) continue;
                        if ( f.lastModified() <= af.getLastUpdateTime() ) {
                            continue;
                        }
                        
                        //load words form the lexicon files
                        try {
                            BufferedReader reader = new BufferedReader(new FileReader(f));
                            String line = null;
                            while ( ( line = reader.readLine() ) != null ) {
                                line = line.trim();
                                if ( line.indexOf('#') != -1 ) continue;
                                if ( "".equals(line) ) continue; 
                                load(paths[i] + "/" + line);
                            }
                            
                            reader.close();
                            
                            FileWriter fw = new FileWriter(f);
                            fw.write("");
                            fw.close();
                            
                            //update the last update time
                            //@Note: some file system may close the in-time last update time update
                            //    in that case, this won't work normally.
                            //but, it will still work!!!
                            af.setLastUpdateTime(f.lastModified());
                            //System.out.println("newly added words loaded for path " + f.getParent());
                        } catch (IOException e) {
                            break;
                        }
                    }
                    
                }
            }
            
        });
        
        autoloadThread.setDaemon(true);
        autoloadThread.start();
        //System.out.println("lexicon autoload thread started!!!");
    }
    
    public void stopAutoload() 
    {
        if ( autoloadThread != null ) {
            autoloadThread.interrupt();
            autoloadThread = null;
        }
    }
    
    public boolean isSync()
    {
        return sync;
    }
    
    /**
     * loop up the dictionary, check the given key is in the dictionary or not
     * 
     * @param t
     * @param key
     * @return true for matched false for not match.
     */
    public abstract boolean match( int t, String key );
    
    /**
     * directly add a IWord item to the dictionary
     * 
     * @param t
     * @param word
    */
    public abstract void add( int t, IWord word );
    
    /**
     * add a new word to the dictionary with its statistics frequency
     * 
     * @param t
     * @param key
     * @param fre
     * @param type
     * @param entity
     */
    public abstract void add( int t, String key, int fre, int type, String entity );
    
    /**
     * add a new word to the dictionary
     * 
     * @param t
     * @param key
     * @param fre
     * @param type
     */
    public abstract void add( int t, String key, int fre, int type );
    
    /**
     * add a new word to the dictionary
     * 
     * @param t
     * @param key
     * @param type
     */
    public abstract void add( int t, String key, int type );
    
    /**
     * add a new word to the dictionary
     * 
     * @param t
     * @param key
     * @param type
     * @param entity
     */
    public abstract void add( int t, String key, int type, String entity );
    
    /**
     * return the IWord asscociate with the given key.
     * if there is not mapping for the key null will be return
     * 
     * @param t
     * @param key
     */
    public abstract IWord get( int t, String key );
    
    /**
     * remove the mapping associate with the given key
     * 
     * @param t
     * @param key
     */
    public abstract void remove( int t, String key );
    
    /**
     * return the size of the dictionary
     * 
     * @param    t
     * @return int
     */
    public abstract int size(int t);
    
    
    /**
     * get the key's type index located in ILexicon interface
     * 
     * @param key
     * @return int
     */
    public static int getIndex( String key )
    {
        if ( key == null ) {
            return -1;
        }
        
        key = key.toUpperCase();
        if ( key.startsWith("CJK_WORD") ) {
            return ILexicon.CJK_WORD;
        } else if ( key.startsWith("CJK_CHAR") ) {
            return ILexicon.CJK_CHAR;
        } else if ( key.startsWith("CJK_UNIT") ) {
            return ILexicon.CJK_UNIT;
        } else if ( key.startsWith("CN_LNAME_ADORN") ) {
            return ILexicon.CN_LNAME_ADORN;
        } else if ( key.startsWith("CN_LNAME") ) {
            return ILexicon.CN_LNAME;
        } else if ( key.startsWith("CN_SNAME") ) {
            return ILexicon.CN_SNAME;
        } else if ( key.startsWith("CN_DNAME_1") ) {
            return ILexicon.CN_DNAME_1;
        } else if ( key.startsWith("CN_DNAME_2") ) {
            return ILexicon.CN_DNAME_2;
        } else if ( key.startsWith("STOP_WORD") ) {
            return ILexicon.STOP_WORD;
        } else if ( key.startsWith("EN_WORD") ) {
            return ILexicon.EN_WORD;
        }
            
        return ILexicon.CJK_WORD;
    }
    
    public JcsegTaskConfig getConfig()
    {
        return config;
    }
    
    public void setConfig( JcsegTaskConfig config )
    {
        this.config = config;
    }
    
    /**
     * load all the words in the specified lexicon file into the dictionary
     * 
     * @param   config
     * @param   dic
     * @param   file
     * @throws  IOException 
     * @throws  FileNotFoundException 
     * @throws  NumberFormatException 
     */
    public static void loadWords( JcsegTaskConfig config, ADictionary dic, File file ) 
            throws NumberFormatException, FileNotFoundException, IOException 
    {
        loadWords(config, dic, new FileInputStream(file));
    }
    
    /**
     * load all the words from a specified lexicon file path
     * 
     * @param   config
     * @param   dic
     * @param   file
     * @throws  IOException 
     * @throws  FileNotFoundException 
     * @throws  NumberFormatException 
    */
    public static void loadWords( JcsegTaskConfig config, ADictionary dic, String file ) 
            throws NumberFormatException, FileNotFoundException, IOException
    {
        loadWords(config, dic, new FileInputStream(file));
    }
    
    /**
     * load words from a InputStream
     * 
     * @param   config
     * @param   dic
     * @param   is
     * @throws  IOException 
     * @throws  NumberFormatException 
    */
    public static void loadWords( JcsegTaskConfig config, ADictionary dic, InputStream is ) 
            throws NumberFormatException, IOException
    {
        boolean isFirstLine = true;
        int t = -1;
        String line = null, gEntity = null;
        BufferedReader buffReader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        
        while ( (line = buffReader.readLine()) != null ) {
            line = line.trim();
            if ( "".equals(line) ) continue;
            //skip the notes
            if ( line.charAt(0) == '#' && line.length() > 1 ) {
                continue;
            }
            
            //the first line for the lexicon file.
            if ( isFirstLine == true ) {
                t = ADictionary.getIndex(line);
                //System.out.println(line+", "+t);
                isFirstLine = false;
                if ( t >= 0 ) {
                    continue;
                }
            }
            
            /*
             * @Note: added at 2016/11/14
             * dictionary directive compile and apply 
            */
            if ( line.charAt(0) == ':' && line.length() > 1 ) {
                String[] directive = line.substring(1).toLowerCase().split("\\s+");
                if ( directive[0].equals("entity") ) {     //@since 2.0.1
                    if ( directive.length > 1 ) {
                        String args = directive[1].trim();
                        gEntity = "null".equals(args) ? null : Entity.get(args);
                    }
                }
                
                continue;
            }
            
            switch ( t ) {
            case ILexicon.CN_SNAME:
            case ILexicon.CN_LNAME:
            case ILexicon.CN_DNAME_1:
            case ILexicon.CN_DNAME_2:
                if ( line.length() == 1 ) {
                    dic.add(t, line, IWord.T_CJK_WORD);
                }
                break;
            case ILexicon.CJK_UNIT:
                /*
                 * for the entity recognition
                 * we may need the unit to help to 
                 * define the numeric entity in front of it
                 * @date 2016/11/12
                */
                if ( line.indexOf('/') == -1 ) {
                    dic.add(t, line, IWord.T_CJK_WORD, gEntity);
                } else {
                    String[] wd = line.split("/");
                    String entity = "null".equals(wd[1]) ? null : Entity.get(wd[1]);
                    dic.add(t, wd[0], IWord.T_CJK_WORD, entity);
                }
                break;
            case ILexicon.CN_LNAME_ADORN:
                dic.add(t, line, IWord.T_CJK_WORD);
                break;
            case ILexicon.STOP_WORD:
                char fChar = line.charAt(0);
                if ( fChar <= 127 || (fChar > 127 
                        && line.length() <= config.MAX_LENGTH) ) {
                    dic.add(ILexicon.STOP_WORD, line, IWord.T_CJK_WORD);
                }
                break;
            case ILexicon.EN_WORD :
            case ILexicon.CJK_WORD:
            case ILexicon.CJK_CHAR:
                String[] wd = line.split("/");
                if ( wd.length < 4 ) {    //format check
                    System.out.println("Word: \"" + wd[0] + "\" format error. -ignored");
                    continue;
                }
                
                if ( t == ILexicon.CJK_CHAR ) {    //single word degree check
                    if ( ! StringUtil.isDigit(wd[4]) ) {
                        System.out.println("Word: \"" + wd[0] + 
                                "\" format error(single word degree should be an integer). -ignored");
                        continue;
                    }
                }
                
                //length limit(CJK_WORD only)
                boolean isCJKWord = (t == ILexicon.CJK_WORD);
                if ( isCJKWord && wd[0].length() > config.MAX_LENGTH ) {
                    continue;
                }
                
                IWord w = dic.get(t, wd[0]);
                if ( w == null ) {
                    if ( t == ILexicon.CJK_CHAR ) {
                        dic.add(ILexicon.CJK_WORD, wd[0], Integer.parseInt(wd[4]), IWord.T_CJK_WORD);
                        w = dic.get(ILexicon.CJK_WORD, wd[0]);
                    } else {
                        dic.add(t, wd[0], IWord.T_CJK_WORD);
                        w = dic.get(t, wd[0]);
                    }
                }
                
                //set the Pinyin of the word.
                if ( config.LOAD_CJK_PINYIN && ! "null".equals(wd[2]) ) {
                    w.setPinyin(wd[2]);
                }
                
                /*
                 * @Note: added at 2016/11/14
                 * update the entity string for CJK and English words only 
                */
                if ( config.LOAD_CJK_ENTITY && t != ILexicon.CJK_CHAR ) {
                    String oEntity = w.getEntity();
                    if ( oEntity == null ) {
                        if ( wd.length > 4 ) {
                            w.setEntity("null".equals(wd[4]) ? null : Entity.get(wd[4]));
                        } else {
                            w.setEntity(gEntity);
                        }
                    } else if ( wd.length > 4 ) {
                        if ( "null".equals(wd[4]) ) {
                            w.setEntity(null);
                        } else if ( wd[4].length() > oEntity.length() ) {
                            w.setEntity(Entity.get(wd[4]));
                        }
                    } else if ( gEntity != null 
                            && gEntity.length() > oEntity.length() ){
                        w.setEntity(gEntity);
                    }
                }
                
                //update the synonym of the word.
                String[] arr = w.getSyn();
                if ( config.LOAD_CJK_SYN && ! "null".equals(wd[3]) ) {
                    String[] syns = wd[3].split(",");
                    for ( int j = 0; j < syns.length; j++ ) {
                        syns[j] = syns[j].trim();
                        /* Here:
                         * filter the synonym that its length 
                         * is greater than config.MAX_LENGTH
                         */
                        if ( isCJKWord && syns[j].length() > config.MAX_LENGTH ) {
                            continue;
                        }
                        
                        /* Here:
                         * check the synonym is not exists, make sure
                         * the same synonym won't appended. (dictionary reload)
                         * 
                         * @date 2013-09-02
                         */
                        boolean add = true;
                        if ( arr != null ) {
                            for ( int i = 0; i < arr.length; i++ )  {
                                if ( syns[j].equals(arr[i]) ) {
                                    add = false;
                                    break;
                                }
                            }
                        }
                        
                        if ( add ) {
                            w.addSyn(syns[j]);
                        }
                    }
                }
                
                //update the word's part of speech
                arr = w.getPartSpeech();
                if ( config.LOAD_CJK_POS && ! "null".equals(wd[1]) ) {
                    String[] pos = wd[1].split(",");
                    
                    for ( int j = 0; j < pos.length; j++ ) {
                        pos[j] = pos[j].trim();
                        
                        /* Here:
                         * check the part of speech is not exists, make sure
                         * the same part of speech won't appended.(dictionary reload)
                         * 
                         * @date 2013-09-02
                         */
                        boolean add = true;
                        if ( arr != null ) {
                            for ( int i = 0; i < arr.length; i++ )  {
                                if ( pos[j].equals(arr[i]) ) {
                                    add = false;
                                    break;
                                }
                            }
                        }
                        
                        if ( add ) {
                            w.addPartSpeech(pos[j].trim());
                        }
                    }
                }
                
                break;
            }   //end of switch
            
        }
        
        buffReader.close();
        buffReader = null;
    }
    
}
