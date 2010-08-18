/* GNU Prolog for Java
 * Copyright (C) 1997-1999  Constantine Plotnikov
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA  02111-1307, USA. The text of license can be also found
 * at http://www.gnu.org/copyleft/lgpl.html
 */
package gnu.prolog.vm.buildins.io;

import gnu.prolog.io.PrologStream;
import gnu.prolog.term.AtomTerm;
import gnu.prolog.term.Term;
import gnu.prolog.term.VariableTerm;
import gnu.prolog.vm.ExecuteOnlyCode;
import gnu.prolog.vm.Interpreter;
import gnu.prolog.vm.PrologException;
import gnu.prolog.vm.TermConstants;

/**
 * prolog code
 */
public class Predicate_peek_char extends ExecuteOnlyCode
{
	@Override
	public int execute(Interpreter interpreter, boolean backtrackMode, gnu.prolog.term.Term args[])
			throws PrologException
	{
		PrologStream stream = interpreter.getEnvironment().resolveStream(args[0]);
		Term inchar = args[1];
		if (inchar instanceof AtomTerm)
		{
			AtomTerm ch = (AtomTerm) inchar;
			if (!(ch == PrologStream.endOfFileAtom) & !(ch.value.length() == 1))
			{
				PrologException.typeError(TermConstants.inCharacterAtom, inchar);
			}
		}
		else if (!(inchar instanceof VariableTerm))
		{
			PrologException.typeError(TermConstants.inCharacterAtom, inchar);
		}
		int inch = stream.peekCode(args[0], interpreter);
		Term rc;
		if (inch == -1)
		{
			rc = PrologStream.endOfFileAtom;
		}
		else
		{
			rc = AtomTerm.get((char) inch);
		}
		return interpreter.unify(inchar, rc);
	}
}
