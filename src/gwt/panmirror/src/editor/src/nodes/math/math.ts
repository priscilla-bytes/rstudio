/*
 * math.ts
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


/*
 * math.ts
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

import { Plugin, PluginKey } from 'prosemirror-state';
import { Node as ProsemirrorNode, DOMOutputSpec, Schema } from 'prosemirror-model';

import { Extension, ExtensionContext } from '../../api/extension';
import { PandocTokenType, PandocToken, PandocOutput } from '../../api/pandoc';
import { kCodeText } from '../../api/code';


import './math-styles.css';
import { EditorView } from 'prosemirror-view';
import { MathNodeView } from './math-view';

const kInlineMathPattern = '\\$[^ ].*?[^\\ ]\\$';
const kInlineMathRegex = new RegExp(kInlineMathPattern);

const kSingleLineDisplayMathPattern = '\\$\\$[^\n]*?\\$\\$';
const kSingleLineDisplayMathRegex = new RegExp(kSingleLineDisplayMathPattern);

export enum MathType {
  Inline = 'InlineMath',
  Display = 'DisplayMath',
}

const MATH_TYPE = 0;
const MATH_CONTENT = 1;

const extension = (context: ExtensionContext): Extension | null => {
  const { pandocExtensions, math, format } = context;

  if (!pandocExtensions.tex_math_dollars) {
    return null;
  }

  // special blogdown handling for markdown renderers that don't support math
  const blogdownMathInCode = format.rmdExtensions.blogdownMathInCode;

  // check if we have math rendering support
  const renderMath = !!math;

  return {

    nodes: [
      {
        name: 'math',
        spec: {
          group: 'inline',
          content: 'inline*',
          inline: true,
          attrs: {
            type: {},
          },
          parseDOM: [
            {
              tag: "span[class*='math']",
              getAttrs(dom: Node | string) {
                const el = dom as Element;
                return {
                  type: el.getAttribute('data-type'),
                };
              },
              preserveWhitespace: 'full',
            },
          ],

          toDOM(node: ProsemirrorNode): DOMOutputSpec {
            return [
              'span',
              {
                class: 'math pm-fixedwidth-font pm-light-text-color',
                'data-type': node.attrs.type,
                spellcheck: 'false',
              },
              0
            ];
          },
        },
        pandoc: {
          readers: [
            {
              token: PandocTokenType.Math,
              node: 'math',
              getAttrs: (tok: PandocToken) => {
                return {
                  type: tok.c[MATH_TYPE].t,
                };
              },
              getText: (tok: PandocToken) => {
                const delimter = delimiterForType(tok.c[MATH_TYPE].t);
                return delimter + tok.c[MATH_CONTENT] + delimter;
              },
            },
            // extract math from backtick code for blogdown
            ...(blogdownMathInCode
              ? [
                {
                  token: PandocTokenType.Code,
                  mark: 'math',
                  match: (tok: PandocToken) => {
                    const text = tok.c[kCodeText];
                    return kSingleLineDisplayMathRegex.test(text) || kInlineMathRegex.test(text);
                  },
                  getAttrs: (tok: PandocToken) => {
                    const text = tok.c[kCodeText];
                    return {
                      type: kSingleLineDisplayMathRegex.test(text) ? MathType.Display : MathType.Inline,
                    };
                  },
                  getText: (tok: PandocToken) => {
                    return tok.c[kCodeText];
                  },
                },
              ]
              : []),
          ],
          writer: (output: PandocOutput, node: ProsemirrorNode) => {

            let equation = node.textContent;

            // if this is blogdownMathInCode just write the content in a code mark
            if (blogdownMathInCode) {
              output.writeToken(PandocTokenType.Code, () => {
                output.writeAttr();
                output.write(equation);
              });
            } else {
              // check for delimeter (if it's gone then write this w/o them math mark)
              const delimiter = delimiterForType(node.attrs.type);
              if (equation.startsWith(delimiter) && equation.endsWith(delimiter)) {
                // remove delimiter
                equation = equation.substr(delimiter.length, equation.length - 2 * delimiter.length);

                // if it's just whitespace then it's not actually math (we allow this state
                // in the editor because it's the natural starting place for new equations)
                if (equation.trim().length === 0) {
                  output.writeText(delimiter + equation + delimiter);
                } else {
                  output.writeToken(PandocTokenType.Math, () => {
                    // write type
                    output.writeToken(
                      node.attrs.type === MathType.Inline ? PandocTokenType.InlineMath : PandocTokenType.DisplayMath,
                    );
                    output.write(equation);
                  });
                }
              } else {
                // user removed the delimiter so write the content literally. when it round trips
                // back into editor it will no longer be parsed by pandoc as math
                output.writeRawMarkdown(equation);
              }
            }
          },
        },
      },
    ],

    plugins: (schema: Schema) => {
      const plugins: Plugin[] = [];

      if (renderMath) {
        plugins.push(
          new Plugin({
            key: new PluginKey('mathview'),
            props: {
              nodeViews: {
                math(node: ProsemirrorNode, view: EditorView, getPos: boolean | (() => number)) {
                  return new MathNodeView(node, view, math!, getPos as () => number);
                },
              },
            },
          }),
        );
      }


      return plugins;
    },

  };
};


export function delimiterForType(type: string) {
  if (type === MathType.Inline) {
    return '$';
  } else {
    return '$$';
  }
}

export default extension;
