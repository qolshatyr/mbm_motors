"""Guards the storefront against browser features missing in the oldest supported browsers.

The floor is set in frontend/vite.config.ts: Chrome 109 (the last one for Windows 7/8),
Safari 15.4 (iPhones that stopped at iOS 15) and Firefox 115. The build lowers syntax and CSS
to that floor but cannot add missing APIs, so the source must not call them. The demo
transport (frontend/src/demo) runs only in the client prototype and is not checked.
"""
from pathlib import Path
import re
import unittest

FRONTEND = Path(__file__).parents[1] / 'frontend'
SOURCES = [path for path in sorted((FRONTEND / 'src').rglob('*')) if path.suffix in ('.ts', '.tsx') and 'demo' not in path.parts]

# Feature -> pattern. Each one is absent in at least one browser of the floor.
SCRIPT = {
    'URLSearchParams.size (Chrome 113, Safari 17)': r'\b(params|next|search|query|updated|filter)\w*\.size\b',
    'Array.prototype.toSorted/toReversed/toSpliced/with (Chrome 110, Safari 16)': r'\.(toSorted|toReversed|toSpliced|with)\(',
    'Object.groupBy / Map.groupBy (Chrome 117, Safari 17.4)': r'\b(Object|Map)\.groupBy\(',
    'Promise.withResolvers (Chrome 119, Safari 17.4)': r'Promise\.withResolvers\(',
    'Array.fromAsync (Chrome 121, Safari 16.4)': r'Array\.fromAsync\(',
    'AbortSignal.timeout / any (Safari 16 / 17.4)': r'AbortSignal\.(timeout|any)\(',
    'URL.canParse (Chrome 120, Safari 17)': r'URL\.canParse\(',
    'Set methods (Chrome 122, Safari 17)': r'\.(union|intersection|difference|symmetricDifference|isSubsetOf|isSupersetOf|isDisjointFrom)\(',
    'String.prototype.isWellFormed (Chrome 111, Safari 16.4)': r'\.(isWellFormed|toWellFormed)\(',
    'form.requestSubmit (Safari 16)': r'\.requestSubmit\(',
    'Popover API (Chrome 114, Safari 17)': r'\.(showPopover|hidePopover|togglePopover)\(',
    'View transitions (Chrome 111, Safari 18)': r'startViewTransition',
    'Intl.Segmenter (Firefox 125)': r'Intl\.Segmenter',
}
STYLE = {
    'color-mix() (Chrome 111, Safari 16.2); the build cannot lower it when it uses variables': r'color-mix\(',
    'Relative colors (Chrome 119, Safari 16.4)': r'\b(rgb|hsl|oklch)\(from ',
    'Container queries (Safari 16)': r'@container',
    'Subgrid (Chrome 117, Safari 16)': r'subgrid',
    'text-box / field-sizing / light-dark (not in the floor)': r'text-box|field-sizing|light-dark\(',
    'Anchor positioning (not in the floor)': r'anchor-name|position-anchor',
    '@starting-style / @scope (not in the floor)': r'@starting-style|@scope',
}


class FrontendCompatibilityTest(unittest.TestCase):
    def offenders(self, paths, patterns):
        found = []
        for path in paths:
            for number, line in enumerate(path.read_text(encoding='utf-8').splitlines(), 1):
                if line.lstrip().startswith(('//', '*', '/*')):
                    continue
                for feature, pattern in patterns.items():
                    if re.search(pattern, line):
                        found.append(f'{path.relative_to(FRONTEND)}:{number}: {feature}')
        return found

    def test_sources_are_found(self):
        self.assertGreater(len(SOURCES), 20)

    def test_scripts_avoid_apis_missing_in_supported_browsers(self):
        self.assertEqual([], self.offenders(SOURCES, SCRIPT))

    def test_styles_avoid_features_missing_in_supported_browsers(self):
        self.assertEqual([], self.offenders(sorted((FRONTEND / 'src/styles').glob('*.css')), STYLE))

    def test_build_targets_the_supported_browsers(self):
        config = (FRONTEND / 'vite.config.ts').read_text(encoding='utf-8')
        for browser in ('chrome109', 'firefox115', 'safari15.4'):
            self.assertIn(browser, config)
        self.assertRegex(config, r'build:\s*\{\s*target: browsers,\s*cssTarget: browsers')

    def test_the_guard_catches_the_header_search_regression(self):
        line = "navigate(`/search${next.size ? `?${next}` : ''}`);"
        self.assertTrue(re.search(SCRIPT['URLSearchParams.size (Chrome 113, Safari 17)'], line))


if __name__ == '__main__':
    unittest.main()
