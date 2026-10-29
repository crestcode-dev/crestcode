/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/
import * as nls from '../../../../nls.js';
// Import the effects we need
import { registerColor, transparent } from '../colorUtils.js';
// Import the colors we need
import { contrastBorder, activeContrastBorder, foreground } from './baseColors.js';
import { selectForeground, selectBackground } from './inputColors.js';
import { listActiveSelectionBackground, listActiveSelectionForeground } from './listColors.js';
export const menuBorder = registerColor('menu.border', { dark: null, light: null, hcDark: contrastBorder, hcLight: contrastBorder }, nls.localize(2077, "Border color of menus."));
export const menuForeground = registerColor('menu.foreground', selectForeground, nls.localize(2078, "Foreground color of menu items."));
export const menuBackground = registerColor('menu.background', selectBackground, nls.localize(2079, "Background color of menu items."));
export const menuSelectionForeground = registerColor('menu.selectionForeground', listActiveSelectionForeground, nls.localize(2080, "Foreground color of the selected menu item in menus."));
export const menuSelectionBackground = registerColor('menu.selectionBackground', listActiveSelectionBackground, nls.localize(2081, "Background color of the selected menu item in menus."));
export const menuSelectionBorder = registerColor('menu.selectionBorder', { dark: null, light: null, hcDark: activeContrastBorder, hcLight: activeContrastBorder }, nls.localize(2082, "Border color of the selected menu item in menus."));
export const menuSeparatorBackground = registerColor('menu.separatorBackground', { dark: transparent(foreground, 0.2), light: transparent(foreground, 0.2), hcDark: contrastBorder, hcLight: contrastBorder }, nls.localize(2083, "Color of a separator menu item in menus."));
//# sourceMappingURL=menuColors.js.map