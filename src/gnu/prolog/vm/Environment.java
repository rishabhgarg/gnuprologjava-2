/* GNU Prolog for Java
 * Copyright (C) 1997-1999  Constantine Plotnikov
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA  02111-1307, USA. The text ol license can be also found 
 * at http://www.gnu.org/copyleft/lgpl.html
 */
package gnu.prolog.vm;
import gnu.prolog.database.Module;
import gnu.prolog.database.Predicate;
import gnu.prolog.database.PredicateListener;
import gnu.prolog.database.PredicateUpdatedEvent;
import gnu.prolog.database.PrologTextLoaderError;
import gnu.prolog.database.PrologTextLoaderState;
import gnu.prolog.io.OperatorSet;
import gnu.prolog.term.AtomTerm;
import gnu.prolog.term.CompoundTerm;
import gnu.prolog.term.CompoundTermTag;
import gnu.prolog.term.IntegerTerm;
import gnu.prolog.term.JavaObjectTerm;
import gnu.prolog.term.Term;
import gnu.prolog.term.VariableTerm;
import gnu.prolog.vm.interpreter.InterpretedCodeCompiler;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
/** this class represent prolog processor. 
  */
public class Environment implements PredicateListener
{
  OperatorSet opSet = new OperatorSet();
  /** current state of loaded database */
  PrologTextLoaderState prologTextLoaderState = new PrologTextLoaderState(); 
  /** predicate which used instead of real code when predicate is not defined */
  PrologCode undefinedPredicate; 
  /** PredicateTag to code mapping */
  HashMap<CompoundTermTag,PrologCode>    tag2code = new HashMap<CompoundTermTag,PrologCode>();
  // flag atoms
  public final static AtomTerm boundedAtom = AtomTerm.get("bounded");
  public final static AtomTerm trueAtom = AtomTerm.get("true");
  public final static AtomTerm falseAtom = AtomTerm.get("false");
  public final static AtomTerm maxIntegerAtom = AtomTerm.get("max_integer");
  public final static AtomTerm minIntegerAtom = AtomTerm.get("min_integer");
  public final static AtomTerm integerRoundingFunctionAtom = AtomTerm.get("integer_rounding_function");
  public final static AtomTerm downAtom = AtomTerm.get("down");
  public final static AtomTerm towardZeroAtom = AtomTerm.get("toward_zero");
  public final static AtomTerm charConversionAtom = AtomTerm.get("char_conversion");
  public final static AtomTerm onAtom = AtomTerm.get("on");
  public final static AtomTerm offAtom = AtomTerm.get("off");
  public final static AtomTerm debugAtom = AtomTerm.get("debug");
  public final static AtomTerm maxArityAtom = AtomTerm.get("max_arity");
  public final static AtomTerm unknownAtom = AtomTerm.get("unknown");
  public final static AtomTerm errorAtom = AtomTerm.get("error");
  public final static AtomTerm failAtom = AtomTerm.get("fail");
  public final static AtomTerm warningAtom = AtomTerm.get("warning");
  public final static AtomTerm doubleQuotesAtom = AtomTerm.get("double_quotes");
  public final static AtomTerm charsAtom = AtomTerm.get("chars");
  public final static AtomTerm codesAtom = AtomTerm.get("codes");
  public final static AtomTerm atomAtom = AtomTerm.get("atom");
  // Integer terms
  public final static IntegerTerm maxIntegerTerm = IntegerTerm.get(Integer.MAX_VALUE);
  public final static IntegerTerm minIntegerTerm = IntegerTerm.get(Integer.MIN_VALUE);
  
  public final static AtomTerm prologFlagAtom = AtomTerm.get("prolog_flag");
  public final static AtomTerm modifyAtom = AtomTerm.get("modify");
  public final static AtomTerm flagAtom = AtomTerm.get("flag");
  public final static CompoundTermTag plusTag = CompoundTermTag.get("+",2);
  /** atom to flag. flags are Terms*/
  HashMap<AtomTerm,Term> atom2flag = new HashMap<AtomTerm,Term> ();
  HashSet<AtomTerm> changableFlags = new HashSet<AtomTerm>();
  boolean initalizationRun = false;
  /** constructor of environment, it loads buildins to database at start. */
  public Environment()
  {
    // load builtins
    CompoundTerm term = new CompoundTerm(AtomTerm.get("resource"), new Term[]{AtomTerm.get("/gnu/prolog/vm/buildins/buildins.pro")});
    ensureLoaded(term); 
    // set flags for environment 
    setPrologFlag(boundedAtom, trueAtom, false);
    setPrologFlag(maxIntegerAtom, maxIntegerTerm, false);
    setPrologFlag(minIntegerAtom, minIntegerTerm, false);
    setPrologFlag(integerRoundingFunctionAtom, downAtom, false);
    setPrologFlag(charConversionAtom, offAtom, true);
    setPrologFlag(debugAtom, offAtom, true);
    setPrologFlag(maxArityAtom, maxIntegerTerm, false);
    setPrologFlag(unknownAtom, errorAtom, true);
    setPrologFlag(doubleQuotesAtom, codesAtom, true);
    initStreams();
  }

  /** get get copy of current state of flags for this environment */
  public synchronized Map<AtomTerm,Term> getPrologFlags()
  {
    return new HashMap<AtomTerm,Term>(atom2flag);
  }
  
  public void runIntialization(Interpreter interpreter) throws PrologException
  {
    if (initalizationRun)
    {
      throw new IllegalStateException("initialization cannot be run again");
    }
    initalizationRun = true;
    Iterator<Term> iterator = getModule().getInitialization().iterator();
    while (iterator.hasNext())
    {
      Term term = iterator.next();
      Interpreter.Goal goal = interpreter.prepareGoal(term);
      int rc = interpreter.execute(goal);
      if (rc == PrologCode.SUCCESS)
      {
        interpreter.stop(goal);
      }
    }
  }

  /** get flag for this environment */
  public synchronized Term getPrologFlag(AtomTerm term)
  {
    return atom2flag.get(term);
  }

  /** get flag for this environment */
  private synchronized void setPrologFlag(AtomTerm term, Term newValue, boolean changable)
  {
    atom2flag.put(term, newValue);
    if (changable)
    {
      changableFlags.add(term);
    }
  }

  public synchronized void setPrologFlag(AtomTerm flag, Term newValue) throws PrologException
  {
    Term value = atom2flag.get(flag);
    if (value == null)
    {
      PrologException.domainError(prologFlagAtom,flag);
    }
    if( flag == boundedAtom)
    {
      if (newValue != trueAtom && newValue != falseAtom)
      {
        PrologException.domainError(prologFlagAtom,new CompoundTerm(plusTag,flag,newValue));
      }
    }
    else if( flag == maxIntegerAtom)
    {
      if (!(newValue instanceof IntegerTerm))
      {
        PrologException.domainError(prologFlagAtom,new CompoundTerm(plusTag,flag,newValue));
      }
    }
    else if( flag == minIntegerAtom)
    {
      if (!(newValue instanceof IntegerTerm))
      {
        PrologException.domainError(prologFlagAtom,new CompoundTerm(plusTag,flag,newValue));
      }
    }
    else if( flag == integerRoundingFunctionAtom)
    {
      if (newValue != downAtom && newValue != towardZeroAtom)
      {
        PrologException.domainError(prologFlagAtom,new CompoundTerm(plusTag,flag,newValue));
      }
    }
    else if( flag == charConversionAtom)
    {
      if (newValue != onAtom && newValue != offAtom)
      {
        PrologException.domainError(prologFlagAtom,new CompoundTerm(plusTag,flag,newValue));
      }
    }
    else if( flag == debugAtom)
    {
      if (newValue != onAtom && newValue != offAtom)
      {
        PrologException.domainError(prologFlagAtom,new CompoundTerm(plusTag,flag,newValue));
      }
    }
    else if( flag == maxArityAtom)
    {
      if (!(newValue instanceof IntegerTerm))
      {
        PrologException.domainError(prologFlagAtom,new CompoundTerm(plusTag,flag,newValue));
      }
    }
    else if( flag == unknownAtom)
    {
      if (newValue != errorAtom && newValue != failAtom && newValue != warningAtom)
      {
        PrologException.domainError(prologFlagAtom,new CompoundTerm(plusTag,flag,newValue));
      }
    }
    else if( flag == doubleQuotesAtom)
    {
      if (newValue != charsAtom && newValue != codesAtom && newValue != atomAtom)
      {
        PrologException.domainError(prologFlagAtom,new CompoundTerm(plusTag,flag,newValue));
      }
    }
    if (!changableFlags.contains(flag))
    {
      PrologException.permissionError(modifyAtom,flagAtom,flag);
    }
    atom2flag.put(flag, newValue);
  }

  public List<PrologTextLoaderError> getLoadingErrors()
  {
    return prologTextLoaderState.getErrors();
  }

  /** ensure that prolog text designated by term is loaded */
  public synchronized void ensureLoaded(Term term)
  {
    if (initalizationRun)
    {
      throw new IllegalStateException("no files can be loaded after inializtion was run");
    }
    prologTextLoaderState.ensureLoaded(term);
  }
  
  /** create interpreter for this environment */
  public Interpreter createInterpreter()
  {
    return new Interpreter(this);
  }
  
  public Module getModule()
  {
    return prologTextLoaderState.getModule();
  }

  /** load code for prolog */
  public synchronized PrologCode loadPrologCode(CompoundTermTag tag) throws PrologException
  {
    //simple variant, later I will need TODO add compilation. 
    Predicate p = prologTextLoaderState.getModule().getDefinedPredicate(tag);
    if (p == null) // case of undefined predicate
    {
      return getUndefinedPredicateCode(tag);
    }
    switch (p.getType())
    {
    case Predicate.CONTROL:
      // really only call should be loaded in this way
    case Predicate.BUILD_IN:
    case Predicate.EXTERNAL:
      {
        try
        {
          Class cls = Class.forName(p.getJavaClassName());
          PrologCode code = (PrologCode)cls.newInstance();
          code.install(this);
          return code;
        } catch (ClassNotFoundException ex)
        {
            System.err.println("Trying to reflect: "
                   + p.getJavaClassName());
            ex.printStackTrace();
            return getUndefinedPredicateCode(tag);
        } catch (InstantiationException e)
        {
            System.err.println("Trying to reflect: "
                    + p.getJavaClassName());
            e.printStackTrace();
            return getUndefinedPredicateCode(tag);
        } catch (IllegalAccessException e)
        {
            System.err.println("Trying to reflect: "
                    + p.getJavaClassName());
            e.printStackTrace();
            return getUndefinedPredicateCode(tag);
        }
      }
    case Predicate.USER_DEFINED:
      {
        PrologCode code = InterpretedCodeCompiler.compile(p.getClauses());
        code.install(this);
        return code;
      }
    default:
      return getUndefinedPredicateCode(tag);
    }
  }

  /** get undefined predicate code */
  public PrologCode getUndefinedPredicateCode(CompoundTermTag tag)
  {
    //if (undefinedPredicate == null)
    //{
    //  undefinedPredicate = null;//= new UndefinedPredicateCode();
    //  undefinedPredicate.install(this);
    //}
    return new UndefinedPredicateCode(tag);
  }


  /** get prolog code */
  public synchronized PrologCode getPrologCode(CompoundTermTag tag) throws PrologException
  {
    PrologCode code = tag2code.get(tag);
    if (code == null)
    {
      code = loadPrologCode(tag);
      tag2code.put(tag,code);
    }
    return code;
  }

  
  HashMap<CompoundTermTag,ArrayList<PrologCodeListenerRef>> tag2listeners = new HashMap<CompoundTermTag,ArrayList<PrologCodeListenerRef>>();
  ReferenceQueue<PrologCodeListenerRef> prologCodeListenerReferenceQueue = new ReferenceQueue<PrologCodeListenerRef>();
  
  private void pollPrologCodeListeners()
  {
    PrologCodeListenerRef ref;
    while (null!=(ref = (PrologCodeListenerRef)prologCodeListenerReferenceQueue.poll()))
    {
      ArrayList<PrologCodeListenerRef> list = tag2listeners.get(ref.tag);
      list.remove(ref);
    }
  }


  private static class PrologCodeListenerRef extends WeakReference<PrologCodeListener>
  {
    PrologCodeListenerRef(ReferenceQueue queue, PrologCodeListener listener, CompoundTermTag tag)
    {
      super(listener, queue);//TODO: possible type error.
      this.tag = tag;
    }
    CompoundTermTag tag;
  }
  
  
  // this functionality will be needed later, but I need to think more ;-)
  /** add prolog code listener */
  public synchronized void addPrologCodeListener(CompoundTermTag tag, PrologCodeListener listener)
  {
    pollPrologCodeListeners();
    ArrayList<PrologCodeListenerRef> list = tag2listeners.get(tag);
    if (list == null)
    {
      list = new ArrayList<PrologCodeListenerRef>();
      tag2listeners.put(tag,list);
    }
    list.add(new PrologCodeListenerRef(prologCodeListenerReferenceQueue,listener,tag));
  }

  /** remove prolog code listener */
  public synchronized void removePrologCodeListener(CompoundTermTag tag, PrologCodeListener listener)
  {
    pollPrologCodeListeners();
    ArrayList<PrologCodeListenerRef> list = tag2listeners.get(tag);
    if (list != null)
    {
      ListIterator<PrologCodeListenerRef> i = list.listIterator();
      while (i.hasNext())
      {
        PrologCodeListenerRef ref = i.next();
        PrologCodeListener lst = ref.get(); 
        if (lst == null)
        {
          i.remove();
        }
        else if (lst == listener)
        {
          i.remove();
          return;
        }
      }
    }
  }

  public synchronized void predicateUpdated(PredicateUpdatedEvent evt)
  {
    Object code = tag2code.remove(evt.getTag());
    pollPrologCodeListeners();
    if (code == null) // if code was not loaded yet 
    {
      return;
    }
    CompoundTermTag tag = evt.getTag();
    ArrayList<PrologCodeListenerRef> list = tag2listeners.get(tag);
    if (list != null)
    {
      PrologCodeUpdatedEvent uevt = new PrologCodeUpdatedEvent(this,tag);
      ListIterator<PrologCodeListenerRef> i = list.listIterator();
      while (i.hasNext())
      {
        PrologCodeListenerRef ref = i.next();
        PrologCodeListener lst = ref.get(); 
        if (lst == null)
        {
          i.remove();
        }
        else
        {
          lst.prologCodeUpdated(uevt);
          return;
        }
      }
    }
  }

  // IO support
  PrologStream userInput;
  PrologStream userOutput;
  PrologStream currentInput;
  PrologStream currentOutput;
  
  ArrayList<PrologStream> openStreams = new ArrayList<PrologStream>();
  HashMap<AtomTerm,PrologStream> alias2stream = new HashMap<AtomTerm,PrologStream>();
  

  public OperatorSet getOperatorSet()
  {
    return opSet;
  }

  private void initStreams()
  {
    try
    {
      PrologStream.OpenOptions inops = new PrologStream.OpenOptions();
      PrologStream.OpenOptions outops = new PrologStream.OpenOptions();
      inops.filename = PrologStream.userInputAtom;
      inops.mode = PrologStream.readAtom;
      inops.aliases.add(PrologStream.userInputAtom);
      inops.eofAction = PrologStream.resetAtom;
      inops.reposition = PrologStream.falseAtom;
      inops.type = PrologStream.textAtom;
      outops.filename = PrologStream.userOutputAtom;
      outops.mode = PrologStream.appendAtom;
      outops.aliases.add(PrologStream.userOutputAtom);
      outops.eofAction = PrologStream.resetAtom;
      outops.reposition = PrologStream.falseAtom;
      outops.type = PrologStream.textAtom;
      userInput = new TextInputPrologStream(inops, new InputStreamReader(System.in));
      userOutput = new TextOutputPrologStream(outops, new OutputStreamWriter(System.out));
      currentInput = userInput;
      currentOutput = userOutput;
      alias2stream.put(PrologStream.userOutputAtom,userOutput);
      alias2stream.put(PrologStream.userInputAtom,userInput);
      openStreams.add(userInput);
      openStreams.add(userOutput);
    }
    catch(Exception ex)
    {
      throw new IllegalStateException("unable to intialize standard streams");
    }
  }

  public synchronized PrologStream getUserInput() throws PrologException
  {
    return userInput;
  }
  
  public synchronized PrologStream getUserOutput() throws PrologException
  {
    return userOutput;
  }
  
  public synchronized PrologStream getCurrentInput() throws PrologException
  {
    return currentInput;
  }
  
  public synchronized PrologStream getCurrentOutput() throws PrologException
  {
    return currentOutput;
  }
  
  public synchronized void setCurrentInput(PrologStream stream) throws PrologException
  {
    currentInput = stream;
  }
  
  public synchronized void setCurrentOutput(PrologStream stream) throws PrologException
  {
    currentOutput = stream;
  }

  public synchronized Map<PrologStream,List<Term>> getStreamProperties() throws PrologException
  {
    HashMap<PrologStream,List<Term>> map = new HashMap<PrologStream,List<Term>>();
    Iterator<PrologStream> srt = openStreams.iterator();
    while (srt.hasNext())
    {
      PrologStream stream = srt.next();
      ArrayList<Term> list = new ArrayList<Term>();
      stream.getProperties(list);
      map.put(stream,list);
    }
    return map;
  }

  public synchronized PrologStream resolveStream(Term stream_or_alias) throws PrologException
  {
    stream_or_alias = stream_or_alias.dereference();
    if (stream_or_alias instanceof VariableTerm)
    {
      PrologException.instantiationError();
    }
    else if (stream_or_alias instanceof AtomTerm)
    {
      PrologStream stream = alias2stream.get(stream_or_alias);
      if (stream == null)
      {
        PrologException.existenceError(PrologStream.streamAtom,stream_or_alias);
      }
      return stream;
    }
    else if (stream_or_alias instanceof JavaObjectTerm)
    {
      JavaObjectTerm jt = (JavaObjectTerm)stream_or_alias;
      if (!(jt.value instanceof PrologStream))
      {
        PrologException.domainError(PrologStream.streamOrAliasAtom,stream_or_alias);
      }
      PrologStream stream = (PrologStream)jt.value;
      if (stream.closed)
      {
        PrologException.existenceError(PrologStream.streamAtom,stream_or_alias);
      }
      return stream;
    }
    else
    {
      PrologException.domainError(PrologStream.streamOrAliasAtom,stream_or_alias);
    }
    return null;
  }
  
  public synchronized Term open(AtomTerm source_sink, AtomTerm mode, PrologStream.OpenOptions options) throws PrologException
  {
    
    Iterator<AtomTerm> ia = options.aliases.iterator();
    while (ia.hasNext())
    {
      AtomTerm a = ia.next();
      if (alias2stream.get(a)!=null)
      {
        PrologException.permissionError(PrologStream.openAtom,
                                            PrologStream.sourceSinkAtom, 
                                            new CompoundTerm(
                                              PrologStream.aliasTag,a));

      }
    }
    PrologStream stream = null;
    if (options.type == PrologStream.binaryAtom)
    {
      stream = new BinaryPrologStream(source_sink, mode, options);
      //PrologException.systemError(); // add handling of this later
    }
    else if (options.type == PrologStream.textAtom)
    {
      if (options.reposition == PrologStream.trueAtom)
      {
        PrologException.permissionError(PrologStream.openAtom,
                                        PrologStream.sourceSinkAtom, 
                                        new CompoundTerm(
                                          PrologStream.repositionTag,
                                          PrologStream.trueAtom));
      }
      if (options.mode == PrologStream.readAtom)
      {
        if (!(new File(source_sink.value)).exists())
        {
          PrologException.existenceError(PrologStream.sourceSinkAtom, source_sink);
        }
        try
        {
          Reader rd = new FileReader(source_sink.value);
          stream = new TextInputPrologStream(options, rd);
        }
        catch(IOException ex)
        {
          PrologException.permissionError(PrologStream.openAtom,
                                          PrologStream.sourceSinkAtom,
                                          source_sink);
        }
      }
      else
      {
        boolean append = (options.mode == PrologStream.appendAtom);
        try
        {
          Writer wr = new FileWriter(source_sink.value,append);
          stream = new TextOutputPrologStream(options, wr);
        }
        catch(IOException ex)
        {
          PrologException.permissionError(PrologStream.openAtom,
                                          PrologStream.sourceSinkAtom,
                                          source_sink);
        }
      }
  
    }
    ia = options.aliases.iterator();
    while (ia.hasNext())
    {
      AtomTerm a = ia.next();
      alias2stream.put(a,stream);
    }
    openStreams.add(stream);
    return stream.streamTerm;
  }

  public synchronized void close(PrologStream stream) throws PrologException
  {
    if (stream == userInput)
    {
      return;
    }
    if (stream == userOutput)
    {
      return;
    }
    Iterator<AtomTerm> aliases = stream.aliases.iterator();
    while (aliases.hasNext())
    {
      alias2stream.remove(aliases.next());
    }
    openStreams.remove(stream);
    if (currentInput == stream)
    {
      currentInput = userInput;
    }
    if (currentOutput == stream)
    {
      currentOutput = userOutput;
    }
  }
}