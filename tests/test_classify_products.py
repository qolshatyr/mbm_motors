import unittest

from tools.catalog.classify_products import classify


def fit(result):
    return [(f['make'], f['model'], f['generation'], f['year_from'], f['year_to']) for f in result['fitments']]


class ClassifyTest(unittest.TestCase):
    def test_shelf_brand_models_and_shared_years(self):
        r = classify('Г3-4 Радиатор кондиционера MBM-AG HY ACCENT/SOLARIS RIO 11-')
        self.assertEqual(r['location'], 'Г3-4')
        self.assertEqual(r['group'], 'G07-03')
        self.assertEqual(r['brands'], ['MBM-AG'])
        self.assertEqual(fit(r), [('Hyundai', 'Accent', None, 2011, None), ('Hyundai', 'Solaris', None, 2011, None),
                                  ('Kia', 'Rio', None, 2011, None)])

    def test_generations_ranges_and_combined_brands(self):
        r = classify('Б3-0  Сайлентблок задного рычага MOBI-LEON HY ELANTRA HD 06- I30 07-12')
        self.assertEqual(r['group'], 'G02-05')
        self.assertEqual(r['brands'], ['MOBIS', 'LEON'])
        self.assertEqual(fit(r), [('Hyundai', 'Elantra', 'HD', 2006, None), ('Hyundai', 'i30', None, 2007, 2012)])
        self.assertEqual((r['year_from'], r['year_to']), (2006, 2012))

    def test_parentheses_generation_engine_and_side(self):
        r = classify('Наконечник рулевой R YULIM-SPEEDMATE-GMB KIA SORENTO(JC)02- 2.5')
        self.assertEqual(r['group'], 'G03-01')
        self.assertEqual(fit(r), [('Kia', 'Sorento', 'JC', 2002, None)])
        self.assertIn('правый', r['position'])
        self.assertEqual(r['brands'], ['YULIM', 'SPEEDMATE', 'GMB'])

    def test_glued_words_lookalike_letters_and_arrows(self):
        r = classify('К6-2 Фильтр салонаSANTA FE CM SONATA NF 05→')
        self.assertEqual(r['group'], 'G09-03')
        self.assertEqual([f['model'] for f in r['fitments']], ['Santa Fe', 'Sonata'])
        r = classify('Прокладка мacлянoгo нacoca ORIG HYUNDAI Santa Fе KIA K5')
        self.assertEqual(r['group'], 'G05-01')
        self.assertIn('Оригинал', r['brands'])
        self.assertEqual([f['model'] for f in r['fitments']], ['Santa Fe', 'K5'])

    def test_to_year_and_side_glued_to_make(self):
        r = classify('Рычаг пер.прав.SPEEDMATE KIA PICANTO (BA) (-2011)')
        self.assertEqual(fit(r), [('Kia', 'Picanto', 'BA', None, 2011)])
        r = classify('Наконечник рулевой тяги LKIA CADENZA/K7 2017-')
        self.assertIn('левый', r['position'])
        self.assertEqual([f['make'] for f in r['fitments']], ['Kia', 'Kia'])

    def test_rule_order_for_ambiguous_parts(self):
        self.assertEqual(classify('Главный цилиндр сцепления HPT HY TUCSON(JM)')['group'], 'G06-01')
        self.assertEqual(classify('Датчик ABS передний правый HPT KIA CERATO 09-')['group'], 'G04-03')
        self.assertEqual(classify('Прокладка масляного фильтра двигателя ORIG')['group'], 'G05-01')
        self.assertEqual(classify('Г5-0 фланец охлаждающей жидкости MOBI-HPT HY ACCENT')['group'], 'G07-02')
        self.assertEqual(classify('Нак.рул.тяги прав.SPEEDMATE KIA PICANTO (TA) (2011-)')['group'], 'G03-01')

    def test_sizes_are_not_years_and_unknowns_are_reported(self):
        r = classify('Граната наружний 27*25*62.2 48T LEON-HPT HY TUCSON(JM)04- SONATA(YF)09- 2.0')
        self.assertEqual(fit(r), [('Hyundai', 'Tucson', 'JM', 2004, None), ('Hyundai', 'Sonata', 'YF', 2009, None)])
        r = classify('Рычаг ZZTOP HY SONATA')
        self.assertIn('ZZTOP', r['unknown'])
        self.assertIn('годы', r['issues'])


if __name__ == '__main__':
    unittest.main()
