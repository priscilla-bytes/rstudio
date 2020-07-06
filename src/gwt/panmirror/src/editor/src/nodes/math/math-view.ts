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
import { EditorUIMath } from "../../api/ui";
import { PromiseQueue } from "../../api/promise";
import { EditorMath } from "../../api/math";

// custom NodeView that accomodates display / interaction with item check boxes
export class MathNodeView implements NodeView {
  public readonly dom: HTMLElement;
  // public readonly contentDOM: HTMLElement;

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
    this.dom = window.document.createElement('span');

    // typeset it
    this.typeset();

  }

  public update(node: ProsemirrorNode, _decos: Decoration[]) {
    if (node.type !== this.node.type) {
      return false;
    }
    this.node = node;
    this.typeset();

    return true;
  }

  private typeset() {
    this.math.typeset(this.dom, this.node.textContent);
  }
}

