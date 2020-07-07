/*
 * math-view.ts
 *
 * Copyright (C) 2020 by RStudio, PBC
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

import { Node as ProsemirrorNode } from "prosemirror-model";
import { NodeView, EditorView, Decoration } from "prosemirror-view";

import debounce from 'lodash.debounce';

import { EditorMath } from "../../api/math";

const kMathEditDebuounceMs = 250;

// custom NodeView that accomodates display / interaction with item check boxes
export class MathNodeView implements NodeView {
  public readonly dom: HTMLElement;
  public readonly mathjaxDOM: HTMLElement;
  public readonly contentDOM: HTMLElement;

  private node: ProsemirrorNode;
  private readonly view: EditorView;
  private readonly getPos: () => number;

  private readonly math: EditorMath;

  constructor(node: ProsemirrorNode, view: EditorView, math: EditorMath, getPos: () => number) {

    this.node = node;
    this.view = view;
    this.getPos = getPos;
    this.math = math;

    // create root span element
    this.dom = window.document.createElement('div');
    this.dom.classList.add('pm-math-view');

    // contentDOM is a div that can be used to edit the math
    this.contentDOM = window.document.createElement('div');
    this.contentDOM.spellcheck = false;
    this.contentDOM.setAttribute('data-type', node.attrs.type);
    this.contentDOM.classList.add(
      'pm-math-view-code',
      'pm-fixedwidth-font',
      'pm-light-text-color',
    );
    this.dom.append(this.contentDOM);

    // mathjax preview
    this.mathjaxDOM = window.document.createElement('div');
    this.mathjaxDOM.classList.add('pm-math-view-mathjax');
    this.mathjaxDOM.style.display = 'none';
    this.mathjaxDOM.contentEditable = "false";
    this.dom.append(this.mathjaxDOM);

    // bind members
    this.typeset = this.typeset.bind(this);
    this.handleTypesetResult = this.handleTypesetResult.bind(this);

    // typeset it
    this.typeset().then(this.handleTypesetResult);

  }

  public update(node: ProsemirrorNode, _decos: Decoration[]) {

    if (node.type !== this.node.type) {
      return false;
    }

    this.node = node;

    this.debouncedTypeset().then(this.handleTypesetResult);

    return true;
  }

  // ignore mutations outside of the contentDOM (MathJax rendering)
  public ignoreMutation(mutation: MutationRecord | { type: 'selection'; target: Element }) {
    return !this.contentDOM || !this.contentDOM.contains(mutation.target);
  }

  private typeset() {
    return this.math.typeset(this.mathjaxDOM, this.node.textContent);
  }

  private debouncedTypeset = debounce(
    this.typeset,
    kMathEditDebuounceMs,
    { leading: true, trailing: true }
  );

  private handleTypesetResult(error: boolean) {
    if (error) {
      this.dom.classList.remove('pm-math-view-rendered');
    } else {
      this.dom.classList.add('pm-math-view-rendered');
    }
  }
}

