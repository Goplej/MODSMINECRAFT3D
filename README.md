# Upgrader Mod

Рулетка апгрейда предметов для **Minecraft Forge 1.20.1** (Java 17).

- **modId:** `upgradermod`
- **автор:** Popipok
- **версия:** 1.0.0

## Возможности

- тёмный GUI с рулеткой и анимированной стрелкой-компасом;
- динамическая оценка предметов по override-файлу, EMC, тегам, рецептам и эвристике;
- множители ставки `x1`, `x2`, `x4`, `x8` и `x10`;
- каталог предметов с поиском;
- предмет `upgradermod:upgrader` и быстрое открытие клавишей `0`;
- серверная проверка ставок, целей, налогов и ограничений;
- звуки начала, успеха и провала прокрутки через зарегистрированные `SoundEvent`.

## Конфигурация

После первого запуска Forge создаёт `config/upgradermod-common.toml`. В секции `[security]`
можно изменить чёрный список предметов:

```toml
[security]
blacklistItems = [
  "minecraft:barrier",
  "minecraft:command_block",
  "minecraft:chain_command_block",
  "minecraft:repeating_command_block",
  "minecraft:structure_block",
  "minecraft:structure_void",
  "minecraft:jigsaw",
  "minecraft:debug_stick",
  "minecraft:light"
]
```

ID из списка не попадают в каталог, не принимаются в слот ставки и не могут быть выбраны
целью. Изменение конфига применяется после перезапуска мира/сервера.

Другие файлы оценки находятся в `config/upgradermod/`:

- `overrides.json` — точные цены предметов;
- `tags.json` — цены тегов Forge.

## Сборка

Требуется Java 17. Из корня проекта выполните:

```bash
./gradlew build
```

Готовый мод после локальной сборки находится в `build/libs/upgradermod-1.20.1-1.0.0.jar`.
Проверенный Java 17 CI-артефакт также хранится в репозитории как
`dist/upgradermod-1.20.1-1.0.0.jar`.

Архив исходников `upgradermod.zip` содержит проект без `.git`, кэшей Gradle,
результатов сборки и готового JAR.
