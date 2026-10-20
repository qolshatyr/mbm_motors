"""Dictionaries for classifying MBM Motors product names (Hyundai/Kia parts warehouse).

Rules are ordered: the first matching part rule wins, so specific phrases come before generic
words. Group codes extend docs/cloudshop/templates/catalog_groups.csv; groups marked "new" were
added for this catalogue.
"""

# Names never contain commas: CloudShop splits the «Категория» (tags) column on «, ».
GROUPS = {
    'G01': 'Кузовные детали',
    'G01-01': 'Бамперы и крепления',
    'G01-02': 'Кузовные панели',
    'G01-03': 'Зеркала',
    'G01-04': 'Решётки и подкрылки',
    'G01-05': 'Замки и стеклоподъёмники',  # new
    'G02': 'Ходовая часть',
    'G02-01': 'Амортизаторы и опоры',
    'G02-02': 'Рычаги и шаровые',
    'G02-03': 'Стойки и втулки стабилизатора',
    'G02-04': 'Ступицы и подшипники',
    'G02-05': 'Пружины и сайлентблоки',
    'G03': 'Рулевое управление',
    'G03-01': 'Наконечники и тяги',
    'G03-02': 'Рейки и комплектующие',
    'G04': 'Тормозная система',
    'G04-01': 'Колодки',
    'G04-02': 'Диски и барабаны',
    'G04-03': 'Суппорты и тормозные шланги',
    'G05': 'Двигатель',
    'G05-01': 'Прокладки и уплотнения',
    'G05-02': 'Поршневая группа',
    'G05-03': 'ГРМ и приводные ремни',
    'G05-04': 'Опоры двигателя',
    'G05-05': 'Топливная система',  # new
    'G05-06': 'Смазка и впуск-выпуск',  # new
    'G06': 'Трансмиссия',
    'G06-01': 'Сцепление',
    'G06-02': 'ШРУСы и приводы',
    'G06-03': 'КПП и комплектующие',
    'G07': 'Охлаждение и климат',
    'G07-01': 'Радиаторы',
    'G07-02': 'Помпы и термостаты',
    'G07-03': 'Кондиционер и отопитель',
    'G08': 'Электрика и освещение',
    'G08-01': 'Фары и фонари',
    'G08-02': 'Датчики',
    'G08-03': 'Стартеры и генераторы',
    'G08-04': 'Зажигание',  # new
    'G08-05': 'Реле и переключатели',  # new
    'G09': 'Фильтры и обслуживание',
    'G09-01': 'Масляные фильтры',
    'G09-02': 'Воздушные фильтры',
    'G09-03': 'Салонные фильтры',
    'G09-04': 'Топливные фильтры',
    'G09-05': 'Щётки стеклоочистителя',
    'G10': 'Масла и технические жидкости',
    'G10-01': 'Моторные масла',
    'G10-02': 'Трансмиссионные масла',
    'G10-03': 'Тормозные и охлаждающие жидкости',
}

# (regex on the lower-cased name without shelf code, group code, part label)
PART_RULES = [
    # Filters and fluids first: «фильтр масляный» must not become an engine part.
    # Filters and fluids only when the name starts with them: «Прокладка масляного фильтра» is a gasket.
    (r'^(сетка\s+)?фильтр\w*\s+(топл|бенз)', 'G09-04', 'Фильтр топливный'),
    (r'^(сетка\s+)?фильтр\w*\s+(масл|акпп|кпп|коробк)', 'G09-01', 'Фильтр масляный'),
    (r'^фильтр\w*\s+воздуш', 'G09-02', 'Фильтр воздушный'),
    (r'^фильтр\w*\s+(салон|кондиц)', 'G09-03', 'Фильтр салонный'),
    (r'^фильтр', 'G09-01', 'Фильтр'),
    (r'щ[её]тк\w*\s+(стекло|дворн)|дворник|стеклоочист', 'G09-05', 'Щётка стеклоочистителя'),
    (r'^масло\s+(акпп|кпп|трансм|atf)|^трансмиссионн\w*\s+масл', 'G10-02', 'Масло трансмиссионное'),
    (r'^масло\b|^моторн\w*\s+масл', 'G10-01', 'Масло моторное'),
    (r'^(антифриз|тосол|тормозн\w*\s+жидк|охлажд\w*\s+жидк|жидкост)', 'G10-03', 'Техническая жидкость'),
    # Brakes.
    (r'колодк', 'G04-01', 'Колодки тормозные'),
    (r'диск\w*\s+торм|тормозн\w*\s+диск|барабан', 'G04-02', 'Диск/барабан тормозной'),
    # Clutch before brake hydraulics: «главный цилиндр сцепления».
    (r'сцеплен|корзин|выжимн', 'G06-01', 'Сцепление'),
    (r'датчик\w*\s+(абс|abs)|(абс|abs)\s+датчик', 'G04-03', 'Датчик ABS'),
    (r'суппорт|шланг\w*\s+тормоз|тормозн\w*\s+шланг|цилиндр\w*\s+тормоз|тормозн\w*\s+цилиндр'
     r'|главный\s+цилиндр|рабочий\s+(тормоз\w*\s+)?цилиндр|вакуум|трос\w*\s+(ручн|стоян|тормоз)|блок\s+abs|ремкомплект\w*\s+суппорт'
     r'|лягушк|направляющ',
     'G04-03', 'Тормозная гидравлика и тросы'),
    # Steering.
    (r'наконечник|\bнак\.?\s*рул|рулев\w*\s+тяг|тяга\s+рулев|тяга\s+рейк', 'G03-01', 'Наконечник/тяга рулевая'),
    (r'рейк|рулев|гидроусил|гур\b|насос\w*\s+гур|гидронасос|кардан', 'G03-02', 'Рулевой механизм'),
    # Suspension.
    (r'стабил', 'G02-03', 'Стойка/втулка стабилизатора'),
    (r'подшипник\w*\s+(опор|аморт)|опорн\w*\s+подшип', 'G02-01', 'Опорный подшипник'),
    (r'амортиз|отбойник|опора\s+(аморт|стойк|передн|задн)|опорн\w*\s+подшип|пыльник\w*\s+(аморт|стойк)',
     'G02-01', 'Амортизатор и опора'),
    (r'стойк', 'G02-01', 'Стойка'),
    # «Сайлентблок рычага» is a bushing, not an arm.
    (r'сайлен|пружин|развальн', 'G02-05', 'Сайлентблок/пружина'),
    (r'шаров|рычаг|\bтяг', 'G02-02', 'Рычаг/шаровая/тяга подвески'),
    (r'ступ|подшипник\w*\s+(передн|задн|колес|пер\b)|цапф|кулак', 'G02-04', 'Ступица/подшипник'),
    # Transmission.
    (r'гранат|шрус|привод|полуос|пыльник\w*\s+(шрус|гранат|привод)|крестовин', 'G06-02', 'ШРУС/привод'),
    (r'акпп|мкпп|\bкпп|коробк|соленоид|гидроблок|кулис|трос\w*\s+(кпп|переключ)|селектор',
     'G06-03', 'КПП и комплектующие'),
    # Cooling and climate before engine words («радиатор масляный», «вентилятор»).
    (r'кондиц|\bконд\b|компрессор|испарител|отопител|печк|вентилятор|реостат|резистор|муфт\w*\s+компрес|конденсер|лопаст|крыльчатк',
     'G07-03', 'Кондиционер/отопитель'),
    (r'радиатор|маслоохлад', 'G07-01', 'Радиатор'),
    (r'помп|термостат|патрубок|шланг|бач[оке]|фланец|тройник|водян\w*\s+насос|охлажд|теплов', 'G07-02', 'Помпа/термостат/патрубок'),
    # Engine.
    (r'подушк|опора\s+двиг|опора\s+кпп|опорн\w*\s+узел|опора\b', 'G05-04', 'Опора двигателя/КПП'),
    (r'прокладк|сальник|уплотн|кольц\w*\s+уплот|герметик|маслосъ[её]м', 'G05-01', 'Прокладка/сальник'),
    (r'поршн|поршен|кольц|вкладыш|шатун|коленвал|колен\w*\s+вал|гильз|палец|полумесяц', 'G05-02', 'Поршневая группа'),
    (r'ремен|ремн|ролик|натяжит|цеп|грм|шестерн|распредвал|башмак|успокоит|шкив|клапан|толкател|гидрокомп|рокер|коромысл|звездочк',
     'G05-03', 'ГРМ/клапаны/ремни'),
    (r'бензонасос|топливн|форсунк|дроссел|инжектор|рампа|регулятор\w*\s+давлен|холостого', 'G05-05', 'Топливная система'),
    (r'масл\w*\s+насос|насос\w*\s+масл|маслоприемник|поддон|коллектор|глушител|катализатор|турбин|egr|егр|впуск|выпуск|гофр|воздуховод|щуп',
     'G05-06', 'Смазка, впуск, выпуск'),
    # Electrics.
    (r'лямбд|лямд|датчик|кислород', 'G08-02', 'Датчик'),
    (r'стартер|генератор|бендикс|втягив|щ[её]тк\w*\s+(генер|стартер)|диодн', 'G08-03', 'Стартер/генератор'),
    (r'катушк|свеч|высоковольт|провод\w*\s+в/в|бронепровод', 'G08-04', 'Зажигание'),
    (r'фар|фонар|ламп|поворотник|противотуман|габарит|стоп-сигнал|катафот', 'G08-01', 'Фары и освещение'),
    (r'реле|переключат|кнопк|шлейф|контактн|замок\s+зажиган|блок\s+управл|разъ[её]м|провод|предохран|мотор|моторчик|сигнал|клаксон',
     'G08-05', 'Реле/переключатели/проводка'),
    # Body.
    (r'бампер|усилител\w*\s+бамп|абсорбер', 'G01-01', 'Бампер и крепления'),
    (r'зеркал', 'G01-03', 'Зеркало'),
    (r'замок|замк|ручк|стеклоподъ[её]м|трос\w*\s+(капот|багаж|замк)|личинк|петл|упор\w*\s+(капот|багаж)|амортизатор\w*\s+(капот|багаж)',
     'G01-05', 'Замок/ручка/стеклоподъёмник'),
    (r'крыл|капот|двер|крышк\w*\s+багаж|багажник', 'G01-02', 'Кузовная панель'),
    (r'решетк|решётк|подкрыл|защит|молдинг|накладк|брызговик|эмблем', 'G01-04', 'Решётка/подкрылок/защита'),
    (r'цилиндр', 'G04-03', 'Тормозной цилиндр'),
    (r'подшипник', 'G02-04', 'Подшипник'),
    (r'ремкомплект|болт|гайк|шпильк|клипс|хомут|втулк|крышк|трос|корпус|насос|кронштейн', 'G05-01', 'Крепёж и комплектующие'),
]

# Car makes. Tokens are matched case-insensitively after splitting glued Cyrillic/Latin words.
MAKE_TOKENS = {
    'HY': 'Hyundai', 'HYUNDAI': 'Hyundai', 'AHYUNDAI': 'Hyundai', 'ATHY': 'Hyundai', 'P.HYUNDAI': 'Hyundai',
    'HYUNDAI-': 'Hyundai', 'HYUNDAI-KIA': 'Hyundai', 'AUTO-HYUNDAI': 'Hyundai', 'HYU': 'Hyundai', 'HYN': 'Hyundai',
    'KIA': 'Kia', 'KI': 'Kia', 'KAI': 'Kia',
    'GENESIS': 'Genesis',
    'TOYOTA': 'Toyota', 'LEXUS': 'Lexus', 'NISSAN': 'Nissan', 'HONDA': 'Honda',
    'MITSUBISHI': 'Mitsubishi', 'DAEWOO': 'Daewoo', 'CHEVROLET': 'Chevrolet',
    'SSANGYONG': 'SsangYong', 'SSANG': 'SsangYong',
}

# Model -> (make, display name). Multi-word models are joined before lookup.
MODELS = {
    # Hyundai
    'ACCENT': ('Hyundai', 'Accent'), 'SOLARIS': ('Hyundai', 'Solaris'), 'VERNA': ('Hyundai', 'Verna'),
    'ELANTRA': ('Hyundai', 'Elantra'), 'ELANTA': ('Hyundai', 'Elantra'), 'ELNATRA': ('Hyundai', 'Elantra'),
    'AVANTE': ('Hyundai', 'Avante'), 'SONATA': ('Hyundai', 'Sonata'), 'SONTA': ('Hyundai', 'Sonata'),
    'TUCSON': ('Hyundai', 'Tucson'), 'TUSCON': ('Hyundai', 'Tucson'), 'IX35': ('Hyundai', 'ix35'),
    'SANTAFE': ('Hyundai', 'Santa Fe'), 'SANTA FE': ('Hyundai', 'Santa Fe'), 'GETZ': ('Hyundai', 'Getz'),
    'I10': ('Hyundai', 'i10'), 'I20': ('Hyundai', 'i20'), 'I30': ('Hyundai', 'i30'), 'I40': ('Hyundai', 'i40'),
    'IX25': ('Hyundai', 'ix25'), 'IX55': ('Hyundai', 'ix55'), 'CRETA': ('Hyundai', 'Creta'),
    'GRANDEUR': ('Hyundai', 'Grandeur'), 'AZERA': ('Hyundai', 'Azera'), 'H-1': ('Hyundai', 'H-1'),
    'H1': ('Hyundai', 'H-1'), 'STAREX': ('Hyundai', 'Starex'), 'SATREX': ('Hyundai', 'Starex'),
    'H100': ('Hyundai', 'H100'), 'H350': ('Hyundai', 'H350'), 'PORTER': ('Hyundai', 'Porter'),
    'PALISADE': ('Hyundai', 'Palisade'), 'KONA': ('Hyundai', 'Kona'), 'MATRIX': ('Hyundai', 'Matrix'),
    'TRAJET': ('Hyundai', 'Trajet'), 'TERRACAN': ('Hyundai', 'Terracan'), 'VERACRUZ': ('Hyundai', 'Veracruz'),
    'GALLOPER': ('Hyundai', 'Galloper'), 'COUPE': ('Hyundai', 'Coupe'), 'TIBURON': ('Hyundai', 'Tiburon'),
    'VELOSTER': ('Hyundai', 'Veloster'), 'STARIA': ('Hyundai', 'Staria'), 'COUNTY': ('Hyundai', 'County'),
    'MIGHTY': ('Hyundai', 'Mighty'), 'CLICK': ('Hyundai', 'Click'), 'LAVITA': ('Hyundai', 'Lavita'),
    'EQUUS': ('Hyundai', 'Equus'), 'GENESIS': ('Genesis', 'Genesis'), 'G80': ('Genesis', 'G80'),
    'ATOZ': ('Hyundai', 'Atos'), 'ATOS': ('Hyundai', 'Atos'), 'IONIQ': ('Hyundai', 'Ioniq'),
    'SOANATA': ('Hyundai', 'Sonata'), 'SOANTA': ('Hyundai', 'Sonata'), 'SONAT': ('Hyundai', 'Sonata'),
    'GRETA': ('Hyundai', 'Creta'), 'ELAMTRA': ('Hyundai', 'Elantra'), 'GRANDUER': ('Hyundai', 'Grandeur'),
    'HYUNDAISONATA': ('Hyundai', 'Sonata'), 'SOLRIS': ('Hyundai', 'Solaris'), 'STAREX-H1': ('Hyundai', 'Starex'),
    'SANTA CRUZ': ('Hyundai', 'Santa Cruz'), 'SANTE': ('Hyundai', 'Santa Fe'), 'PALISEDA': ('Hyundai', 'Palisade'),
    'TUNCSON': ('Hyundai', 'Tucson'), 'GETS': ('Hyundai', 'Getz'), 'ELEANTRA': ('Hyundai', 'Elantra'),
    'LAVITO': ('Hyundai', 'Lavita'), 'SONATANF': ('Hyundai', 'Sonata'), 'BAYON': ('Hyundai', 'Bayon'), 'VENUE': ('Hyundai', 'Venue'), 'CASPER': ('Hyundai', 'Casper'),
    # Kia
    'RIO': ('Kia', 'Rio'), 'CERATO': ('Kia', 'Cerato'), 'FORTE': ('Kia', 'Forte'), 'K3': ('Kia', 'K3'),
    'K5': ('Kia', 'K5'), 'OPTIMA': ('Kia', 'Optima'), 'MAGENTIS': ('Kia', 'Magentis'), 'K7': ('Kia', 'K7'),
    'K8': ('Kia', 'K8'), 'CADENZA': ('Kia', 'Cadenza'), 'SORENTO': ('Kia', 'Sorento'),
    'SPORTAGE': ('Kia', 'Sportage'), 'CARNIVAL': ('Kia', 'Carnival'), 'SEDONA': ('Kia', 'Sedona'),
    'PICANTO': ('Kia', 'Picanto'), 'MORNING': ('Kia', 'Morning'), 'SOUL': ('Kia', 'Soul'),
    'CEED': ('Kia', 'Ceed'), "CEE'D": ('Kia', 'Ceed'), 'CARENS': ('Kia', 'Carens'), 'MOHAVE': ('Kia', 'Mohave'),
    'SELTOS': ('Kia', 'Seltos'), 'STONIC': ('Kia', 'Stonic'), 'SPECTRA': ('Kia', 'Spectra'),
    'SHUMA': ('Kia', 'Shuma'), 'BONGO': ('Kia', 'Bongo'), 'QUORIS': ('Kia', 'Quoris'), 'K9': ('Kia', 'K9'),
    'OPIRUS': ('Kia', 'Opirus'), 'VENGA': ('Kia', 'Venga'), 'NIRO': ('Kia', 'Niro'), 'PRIDE': ('Kia', 'Pride'),
    'CLARUS': ('Kia', 'Clarus'), 'STINGER': ('Kia', 'Stinger'), 'KX3': ('Kia', 'KX3'),
    'SPORTAGEII': ('Kia', 'Sportage'), 'SERATO': ('Kia', 'Cerato'), 'PICANTA': ('Kia', 'Picanto'),
    'TELLURIDE': ('Kia', 'Telluride'), 'SORRENTO': ('Kia', 'Sorento'), 'SOREN': ('Kia', 'Sorento'),
    # Other makes seen in the catalogue
    'AURIS': ('Toyota', 'Auris'), 'CAMRY': ('Toyota', 'Camry'), 'COROLLA': ('Toyota', 'Corolla'),
    'LACETTI': ('Chevrolet', 'Lacetti'), 'NEXIA': ('Daewoo', 'Nexia'), 'MATIZ': ('Daewoo', 'Matiz'),
}

# Body/generation codes. Value: (make, model). Only codes that identify one model are listed.
GENERATIONS = {
    'EF': ('Hyundai', 'Sonata'), 'NF': ('Hyundai', 'Sonata'), 'YF': ('Hyundai', 'Sonata'),
    'LF': ('Hyundai', 'Sonata'), 'DN8': ('Hyundai', 'Sonata'),
    'XD': ('Hyundai', 'Elantra'), 'HD': ('Hyundai', 'Elantra'), 'MD': ('Hyundai', 'Elantra'),
    'UD': ('Hyundai', 'Elantra'), 'AD': ('Hyundai', 'Elantra'), 'CN7': ('Hyundai', 'Elantra'),
    'SM': ('Hyundai', 'Santa Fe'), 'CM': ('Hyundai', 'Santa Fe'), 'DM': ('Hyundai', 'Santa Fe'),
    'TM': ('Hyundai', 'Santa Fe'), 'MX5': ('Hyundai', 'Santa Fe'),
    'JM': ('Hyundai', 'Tucson'), 'LM': ('Hyundai', 'Tucson'), 'TL': ('Hyundai', 'Tucson'),
    'NX4': ('Hyundai', 'Tucson'),
    'LC': ('Hyundai', 'Accent'), 'MC': ('Hyundai', 'Accent'), 'RB': ('Hyundai', 'Solaris'),
    'HCR': ('Hyundai', 'Solaris'), 'HC': ('Hyundai', 'Solaris'),
    'TG': ('Hyundai', 'Grandeur'), 'HG': ('Hyundai', 'Grandeur'), 'IG': ('Hyundai', 'Grandeur'),
    'GS': ('Hyundai', 'Creta'), 'SU2': ('Hyundai', 'Creta'), 'LX2': ('Hyundai', 'Palisade'),
    'TQ': ('Hyundai', 'H-1'), 'OS': ('Hyundai', 'Kona'), 'FD': ('Hyundai', 'i30'), 'GD': ('Hyundai', 'i30'),
    'PD': ('Hyundai', 'i30'), 'VF': ('Hyundai', 'i40'), 'PB': ('Hyundai', 'i20'), 'PA': ('Hyundai', 'i10'),
    'BH': ('Genesis', 'Genesis'), 'DH': ('Genesis', 'Genesis'),
    'JB': ('Kia', 'Rio'), 'UB': ('Kia', 'Rio'), 'QB': ('Kia', 'Rio'), 'YB': ('Kia', 'Rio'), 'FB': ('Kia', 'Rio'),
    'LD': ('Kia', 'Cerato'), 'TD': ('Kia', 'Cerato'), 'YD': ('Kia', 'Cerato'), 'BD': ('Kia', 'Cerato'),
    'MG': ('Kia', 'Magentis'), 'TF': ('Kia', 'Optima'), 'JF': ('Kia', 'Optima'), 'DL3': ('Kia', 'K5'),
    'BL': ('Kia', 'Sorento'), 'XM': ('Kia', 'Sorento'), 'UM': ('Kia', 'Sorento'), 'MQ4': ('Kia', 'Sorento'),
    'KM': ('Kia', 'Sportage'), 'JE': ('Kia', 'Sportage'), 'SL': ('Kia', 'Sportage'), 'QL': ('Kia', 'Sportage'),
    'NQ5': ('Kia', 'Sportage'), 'VQ': ('Kia', 'Carnival'), 'YP': ('Kia', 'Carnival'), 'KA4': ('Kia', 'Carnival'),
    'BA': ('Kia', 'Picanto'), 'TA': ('Kia', 'Picanto'), 'JA': ('Kia', 'Picanto'), 'AM': ('Kia', 'Soul'),
    'PS': ('Kia', 'Soul'), 'SK3': ('Kia', 'Soul'), 'ED': ('Kia', 'Ceed'), 'JD': ('Kia', 'Ceed'), 'CD': ('Kia', 'Ceed'),
    'UN': ('Kia', 'Carens'), 'RP': ('Kia', 'Carens'), 'HM': ('Kia', 'Mohave'), 'SP2': ('Kia', 'Seltos'),
    'VG': ('Kia', 'Cadenza'), 'YG': ('Kia', 'K7'), 'JC': ('Kia', 'Sorento'), 'NC': ('Hyundai', 'Santa Fe'),
    'TB': ('Hyundai', 'Getz'),
}

ENGINE_FAMILIES = {'THETA', 'GAMMA', 'ALPHA', 'BETA', 'NU', 'MU', 'LAMBDA', 'KAPPA', 'EPSILON', 'SIRIUS', 'SMARTSTREAM'}

# Part manufacturers. Value: display name. Combined tokens like LEON-HPT are split on '-'.
BRANDS = {
    'HPT': 'HPT', 'LEON': 'LEON', 'MOBIS': 'MOBIS', 'MOBI': 'MOBIS', 'MOBIPARTS': 'MOBIPARTS',
    'MANDO': 'MANDO', 'STELLOX': 'STELLOX', 'KORWHEEL': 'KORWHEEL', 'KORWHELL': 'KORWHEEL', 'KORW': 'KORWHEEL',
    'MEILLOR': 'MEILLOR', 'MELLOR': 'MEILLOR', 'OSSCA': 'OSSCA', 'OKAMI': 'OKAMI', 'YULIM': 'YULIM',
    'OPTIPOWER': 'OPTIPOWER', 'DONGNAM': 'DONGNAM', 'SPEEDMATE': 'SPEEDMATE', 'SUFIX': 'SUFIX',
    'NIPON': 'NIPON', 'NIPPON': 'NIPON', 'WINKOD': 'WINKOD', 'DINOCO': 'DINOCO', 'ONNURI': 'ONNURI',
    'GUERRIER': 'GUERRIER', 'KBF': 'KBF', 'CHUANRONG': 'CHUANRONG', 'HI-Q': 'HI-Q', 'HIQ': 'HI-Q',
    'DYG': 'DYG', 'DYM': 'DYM', 'AMD': 'AMD', 'BAW': 'BAW', 'PATRON': 'PATRON', 'VALEO': 'VALEO',
    'KORMAX': 'KORMAX', 'PARTS-MALL': 'PARTS-MALL', 'PARTSMALL': 'PARTS-MALL', 'PMC': 'PARTS-MALL',
    'TAIHO': 'TAIHO', 'TONGSHI': 'TONGSHI', 'CORTECO': 'CORTECO', 'NOK': 'NOK', 'LYNX': 'LYNX',
    'DENSO': 'DENSO', 'TORCH': 'TORCH', 'FRAM': 'FRAM', 'TENACITY': 'TENACITY', 'ALNSU': 'ALNSU',
    'CTR': 'CTR', 'CCL': 'CCL', 'GMB': 'GMB', 'KOS': 'KOS', 'NGP': 'NGP', 'POS': 'POS', 'TP': 'TP',
    'TIK': 'TIK', 'SOVKA': 'SOVKA', 'MBM': 'MBM', 'MBM-AG': 'MBM-AG', 'AG': 'AG', 'LUXE': 'LUXE',
    'KIC': 'KIC', 'KW': 'KW', 'BOSCH': 'BOSCH', 'NGK': 'NGK', 'GATES': 'GATES', 'DAYCO': 'DAYCO',
    'CONTITECH': 'CONTITECH', 'SKF': 'SKF', 'NSK': 'NSK', 'NTN': 'NTN', 'KOYO': 'KOYO', 'INA': 'INA',
    'LUK': 'LUK', 'SACHS': 'SACHS', 'KYB': 'KYB', 'VALEO-PHC': 'VALEO', 'PHC': 'PHC', 'SANGSIN': 'SANGSIN',
    'HANKOOK': 'HANKOOK', 'BRAKE': None, 'CAR-DEX': 'CAR-DEX', 'CARDEX': 'CAR-DEX', 'JAPANPARTS': 'JAPANPARTS',
    'GREENFILTER': 'GREENFILTER', 'MAHLE': 'MAHLE', 'MANN': 'MANN', 'FEBI': 'FEBI', 'LEMFORDER': 'LEMFORDER',
 'ORIG': 'Оригинал', 'ORIGINAL': 'Оригинал', 'XORIG': 'Оригинал',
    'GENUINE': 'Оригинал', 'HYUNDAI-ORIG': 'Оригинал', 'DOOWON': 'DOOWON', 'HANON': 'HANON',
    'HALLA': 'HALLA', 'PYUNG': 'PYUNG HWA', 'PHT': 'PHT', 'JAKOPARTS': 'JAKOPARTS', 'NISSHINBO': 'NISSHINBO',
    'AKEBONO': 'AKEBONO', 'TRW': 'TRW', 'ATE': 'ATE', 'BREMBO': 'BREMBO', 'ZIMMERMANN': 'ZIMMERMANN',
    'MASUMA': 'MASUMA', 'MILES': 'MILES', 'DEPO': 'DEPO', 'TYC': 'TYC', 'SANKEI': 'SANKEI', 'GSP': 'GSP',
    'ASAM': 'ASAM', 'ZEKKERT': 'ZEKKERT', 'OSRAM': 'OSRAM', 'PHILIPS': 'PHILIPS', 'IRIDIUM': None,
    'EEUU': 'EEUU', 'SANGWOO': 'SANGWOO', 'TENACITY-HPT': 'TENACITY', 'TRIALLI': 'TRIALLI', 'KPR': 'KPR',
    'FINWHALE': 'FINWHALE', 'NAP': 'NAP', 'KAP': 'KAP', 'HSB': 'HSB', 'JEENICE': 'JEENICE', 'KEFENG': 'KEFENG',
    'HUELALIFT': 'HUELALIFT', 'OSSKA': 'OSSCA', 'MAKINTECH': 'MAKINTECH', 'COWTOTAL': 'COWTOTAL', 'BRAVE': 'BRAVE',
    'DOMO': 'DOMO', 'FEBEST': 'FEBEST', 'TEKIN': 'TEKIN', 'URBW': 'URBW', 'SURAI': 'SURAI', 'ATC': 'ATC',
    'KIYATO': 'KIYATO', 'KOOLXMAX': 'KOOLXMAX', 'MGM': 'MGM', 'KORTEX': 'KORTEX', 'HENKO': 'HENKO', 'VKZ': 'VKZ',
    'PHG': 'PHG', 'KINGA': 'KINGA', 'LLP': 'LLP', 'TBP': 'TBP', 'DSBR': 'DSBR', 'DISCOVER': 'DSBR', '3-X': '3-X',
    'VASURE': 'VASURE', 'PITWORKS': 'PITWORKS', 'INTPARTS': 'INTPARTS', 'STARVOLT': 'STARVOLT',
    'DONGSUNG': 'DONGSUNG', 'DONGIL': 'DONGIL', 'TEIKIN': 'TEIKIN', 'RIKEN': 'RIKEN', 'SANTIAN': 'SANTIAN',
    'X-LINE': 'X-LINE', 'GEPU': 'GEPU', 'ABSMANDO': 'MANDO', 'GEUN-YOUNG': 'GEUN YOUNG', 'MILTON': 'MILTON', 'MILTON-MT': 'MILTON', 'DEQST': 'DEQST', 'GMP': 'GMP', 'SOVKO': 'SOVKA', 'BAIKOR': 'BAIKOR',
    'PART-ONE': 'PART-ONE', 'KASHIYAMA': 'KASHIYAMA', 'KLINEO': 'KLINEO', 'CTI': 'CTI', 'BDM': 'BDM',
    'PLANT-EUR': 'PLANT', 'AUTO-HPT': 'HPT',
    'H-LINE': 'H LINE', 'HLINE': 'H LINE', 'MAX-PARTS': 'MAX PARTS', 'CTRFED': 'CTR', 'KORTEX-HPT': 'KORTEX',
}

COUNTRIES = {'KOREA': 'Корея', 'JAPAN': 'Япония', 'TAIWAN': 'Тайвань', 'CHINA': 'Китай', 'USA': 'США',
             'GERMANY': 'Германия', 'TURKEY': 'Турция', 'КИТАЙ': 'Китай', 'КОРЕЯ': 'Корея', 'ЯПОНИЯ': 'Япония'}

# Uppercase tokens that are neither brands nor vehicle data.
NOISE = {'AT', 'MT', 'ABS', 'STD', 'II', 'III', 'IV', 'V', 'LINE', 'MAX', 'AUTO', 'CO', 'CO.', 'SIZE', 'L', 'R',
         'LH', 'RH', 'FR', 'RR', 'GDI', 'CRDI', 'CRD', 'TDC', 'MPI', 'DOHC', 'SOHC', 'TCI', 'VGT', 'LPG', 'LPI',
         'VVT', 'CVVT', 'TURBO', 'MM', 'PCS', 'SET', 'NEW', 'OLD', 'FULL', 'WD', '2WD', '4WD', 'AWD', 'FWD',
         'D', 'E', 'G', 'GL', 'GLS', 'GT', 'LED', 'HID', 'XENON', 'OEM', 'A', 'B', 'C', 'S', 'X', 'W', 'K', 'M',
         'USA', 'EU', 'RUS', 'KZ', 'DSG', 'CVT', 'ATF', 'SP', 'SPIII', 'SP-III', 'SP-IV', 'API', 'SAE', 'PREMIUM',
         'SPORT', 'PLUS', 'PRO', 'HB', 'SD', 'SW', 'UN', 'IRIDIUM', 'PLATINUM', 'BRAKE', 'LHY', 'RKIA', 'LKIA',
         'O', 'Y', 'HYUNDAIKIA', 'HY/KIA', 'KIA/HY', 'HYBRID', 'HYBIRD', 'ENGI', 'ENGINE', 'PISTON', 'SHOCK',
         'MOTOR', 'WAY', 'STAR', 'EPE', 'EP', 'PY', 'KO', 'VI', 'VII', 'IX', 'VVTI', 'ULTRA', 'YAS', 'RJ', 'VOLT', 'T-GDI', 'TGDI', 'GRAND', 'MALL', 'PARTS', 'NEW-', 'TYPE', 'ASSY', 'ASSEMBLY'}

# Two-word names joined before lookup.
COMPOUNDS = {('SANTA', 'FE'): 'SANTA FE', ('SANTA', 'FF'): 'SANTA FE', ('SANTE', 'FE'): 'SANTA FE', ('GRAND', 'STAREX'): 'STAREX',
             ('H', 'LINE'): 'H-LINE', ('PARTS', 'MALL'): 'PARTS-MALL', ('MAX', 'PARTS'): 'MAX-PARTS',
             ('GEUN', 'YOUNG'): 'GEUN-YOUNG', ('SANTA', 'CRUZ'): 'SANTA CRUZ'}
