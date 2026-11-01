'use strict';
// Universer og sæsontemaer til fanen "Univers". Hver titel: [type, titel, år, alternative titler …, {show, note}]  –  m = film, s = serie.
// "order" fortæller, hvilken rækkefølge listen står i (historiens eller udgivelsernes).

const GROUPS = ['Superhelte', 'Sci-fi', 'Fantasy & eventyr', 'Action', 'Familie & animation'];

const UNIVERSES = [
  // ---------------- Superhelte ----------------
  {
    key: 'marvel', name: 'Marvel', group: 'Superhelte', order: 'historien',
    items: [
      ['m', 'Captain America: The First Avenger', 2011],
      ['s', 'Marvel\'s Agent Carter', 2015, 'Agent Carter'],
      ['m', 'Captain Marvel', 2019],
      ['m', 'Iron Man', 2008],
      ['m', 'Iron Man 2', 2010],
      ['m', 'The Incredible Hulk', 2008],
      ['m', 'Thor', 2011],
      ['m', 'The Avengers', 2012, 'Marvel\'s The Avengers', 'Avengers Assemble'],
      ['m', 'Thor: The Dark World', 2013],
      ['m', 'Iron Man 3', 2013],
      ['m', 'Captain America: The Winter Soldier', 2014],
      ['m', 'Guardians of the Galaxy', 2014],
      ['m', 'Guardians of the Galaxy Vol. 2', 2017, 'Guardians of the Galaxy Vol 2', 'Guardians of the Galaxy Volume 2'],
      ['m', 'Avengers: Age of Ultron', 2015],
      ['m', 'Ant-Man', 2015],
      ['m', 'Captain America: Civil War', 2016],
      ['m', 'Black Widow', 2021],
      ['m', 'Black Panther', 2018],
      ['m', 'Spider-Man: Homecoming', 2017],
      ['m', 'Doctor Strange', 2016],
      ['m', 'Thor: Ragnarok', 2017],
      ['m', 'Ant-Man and the Wasp', 2018],
      ['m', 'Avengers: Infinity War', 2018],
      ['m', 'Avengers: Endgame', 2019],
      ['s', 'Loki', 2021],
      ['s', 'What If...?', 2021, 'What If'],
      ['s', 'WandaVision', 2021],
      ['s', 'The Falcon and the Winter Soldier', 2021],
      ['m', 'Shang-Chi and the Legend of the Ten Rings', 2021],
      ['m', 'Eternals', 2021],
      ['m', 'Spider-Man: Far From Home', 2019],
      ['m', 'Spider-Man: No Way Home', 2021],
      ['m', 'Doctor Strange in the Multiverse of Madness', 2022],
      ['s', 'Hawkeye', 2021],
      ['s', 'Moon Knight', 2022],
      ['m', 'Black Panther: Wakanda Forever', 2022],
      ['s', 'Echo', 2024],
      ['s', 'She-Hulk: Attorney at Law', 2022],
      ['s', 'Ms. Marvel', 2022],
      ['m', 'Thor: Love and Thunder', 2022],
      ['m', 'Werewolf by Night', 2022],
      ['m', 'The Guardians of the Galaxy Holiday Special', 2022],
      ['m', 'Ant-Man and the Wasp: Quantumania', 2023],
      ['m', 'Guardians of the Galaxy Vol. 3', 2023, 'Guardians of the Galaxy Vol 3', 'Guardians of the Galaxy Volume 3'],
      ['s', 'Secret Invasion', 2023],
      ['m', 'The Marvels', 2023],
      ['m', 'Deadpool & Wolverine', 2024, 'Deadpool and Wolverine'],
      ['s', 'Agatha All Along', 2024],
      ['m', 'Captain America: Brave New World', 2025],
      ['s', 'Daredevil: Born Again', 2025],
      ['m', 'Thunderbolts*', 2025, 'Thunderbolts', 'The New Avengers'],
      ['s', 'Ironheart', 2025],
      ['m', 'The Fantastic Four: First Steps', 2025, 'Fantastic Four: First Steps', 'The Fantastic 4: First Steps'],
      ['s', 'Wonder Man', 2026],
      ['m', 'Spider-Man: Brand New Day', 2026],
    ],
  },
  {
    key: 'dc', name: 'DC – Batman & Superman', group: 'Superhelte', order: 'udgivelserne',
    items: [
      ['m', 'Batman Begins', 2005],
      ['m', 'The Dark Knight', 2008],
      ['m', 'The Dark Knight Rises', 2012],
      ['m', 'Man of Steel', 2013],
      ['m', 'Batman v Superman: Dawn of Justice', 2016],
      ['m', 'Wonder Woman', 2017],
      ['m', 'Aquaman', 2018],
      ['m', 'Zack Snyder\'s Justice League', 2021, 'Justice League'],
      ['m', 'The Batman', 2022],
      ['s', 'The Penguin', 2024],
      ['m', 'Superman', 2025],
    ],
  },
  {
    key: 'xmen', name: 'X-Men', group: 'Superhelte', order: 'historien',
    items: [
      ['m', 'X-Men: First Class', 2011],
      ['m', 'X-Men Origins: Wolverine', 2009],
      ['m', 'X-Men', 2000],
      ['m', 'X2', 2003, 'X2: X-Men United', 'X-Men 2'],
      ['m', 'X-Men: The Last Stand', 2006],
      ['m', 'The Wolverine', 2013],
      ['m', 'X-Men: Days of Future Past', 2014],
      ['m', 'X-Men: Apocalypse', 2016],
      ['m', 'Dark Phoenix', 2019, 'X-Men: Dark Phoenix'],
      ['m', 'Deadpool', 2016],
      ['m', 'Deadpool 2', 2018],
      ['m', 'The New Mutants', 2020],
      ['m', 'Logan', 2017],
    ],
  },
  // ---------------- Sci-fi ----------------
  {
    key: 'starwars', name: 'Star Wars', group: 'Sci-fi', order: 'historien',
    items: [
      ['s', 'Star Wars: The Acolyte', 2024, 'The Acolyte'],
      ['s', 'Star Wars: Tales of the Jedi', 2022, 'Tales of the Jedi'],
      ['m', 'Star Wars: Episode I - The Phantom Menace', 1999, 'Star Wars: The Phantom Menace', 'The Phantom Menace', 'Star Wars Episode I'],
      ['m', 'Star Wars: Episode II - Attack of the Clones', 2002, 'Star Wars: Attack of the Clones', 'Attack of the Clones', 'Star Wars Episode II'],
      ['m', 'Star Wars: The Clone Wars', 2008],
      ['s', 'Star Wars: The Clone Wars', 2008, 'The Clone Wars'],
      ['m', 'Star Wars: Episode III - Revenge of the Sith', 2005, 'Star Wars: Revenge of the Sith', 'Revenge of the Sith', 'Star Wars Episode III'],
      ['s', 'Star Wars: Tales of the Empire', 2024, 'Tales of the Empire'],
      ['s', 'Star Wars: The Bad Batch', 2021, 'The Bad Batch'],
      ['m', 'Solo: A Star Wars Story', 2018, 'Solo'],
      ['s', 'Obi-Wan Kenobi', 2022],
      ['s', 'Andor', 2022, 'Star Wars: Andor'],
      ['s', 'Star Wars Rebels', 2014, 'Star Wars: Rebels'],
      ['m', 'Rogue One: A Star Wars Story', 2016, 'Rogue One'],
      ['m', 'Star Wars: Episode IV - A New Hope', 1977, 'Star Wars', 'Star Wars: A New Hope', 'A New Hope', 'Star Wars Episode IV'],
      ['m', 'Star Wars: Episode V - The Empire Strikes Back', 1980, 'The Empire Strikes Back', 'Star Wars: The Empire Strikes Back', 'Star Wars Episode V'],
      ['m', 'Star Wars: Episode VI - Return of the Jedi', 1983, 'Return of the Jedi', 'Star Wars: Return of the Jedi', 'Star Wars Episode VI'],
      ['s', 'The Mandalorian', 2019, 'Star Wars: The Mandalorian'],
      ['s', 'The Book of Boba Fett', 2021, 'Star Wars: The Book of Boba Fett'],
      ['m', 'The Mandalorian and Grogu', 2026, 'The Mandalorian & Grogu'],
      ['s', 'Star Wars: Skeleton Crew', 2024, 'Skeleton Crew'],
      ['s', 'Ahsoka', 2023, 'Star Wars: Ahsoka'],
      ['s', 'Star Wars Resistance', 2018, 'Star Wars: Resistance'],
      ['m', 'Star Wars: Episode VII - The Force Awakens', 2015, 'Star Wars: The Force Awakens', 'The Force Awakens', 'Star Wars Episode VII'],
      ['m', 'Star Wars: Episode VIII - The Last Jedi', 2017, 'Star Wars: The Last Jedi', 'The Last Jedi', 'Star Wars Episode VIII'],
      ['m', 'Star Wars: Episode IX - The Rise of Skywalker', 2019, 'Star Wars: The Rise of Skywalker', 'The Rise of Skywalker', 'Star Wars Episode IX'],
    ],
  },
  {
    key: 'alien', name: 'Alien', group: 'Sci-fi', order: 'historien',
    items: [
      ['m', 'Prometheus', 2012],
      ['m', 'Alien: Covenant', 2017],
      ['s', 'Alien: Earth', 2025],
      ['m', 'Alien', 1979],
      ['m', 'Aliens', 1986],
      ['m', 'Alien: Romulus', 2024],
      ['m', 'Alien³', 1992, 'Alien 3'],
      ['m', 'Alien Resurrection', 1997, 'Alien: Resurrection'],
    ],
  },
  {
    key: 'predator', name: 'Predator', group: 'Sci-fi', order: 'historien',
    items: [
      ['m', 'Prey', 2022],
      ['m', 'Predator: Killer of Killers', 2025],
      ['m', 'Predator', 1987],
      ['m', 'Predator 2', 1990],
      ['m', 'Predators', 2010],
      ['m', 'The Predator', 2018],
      ['m', 'Predator: Badlands', 2025],
    ],
  },
  {
    key: 'terminator', name: 'Terminator', group: 'Sci-fi', order: 'udgivelserne',
    items: [
      ['m', 'The Terminator', 1984, 'Terminator'],
      ['m', 'Terminator 2: Judgment Day', 1991, 'Terminator 2'],
      ['m', 'Terminator 3: Rise of the Machines', 2003],
      ['m', 'Terminator Salvation', 2009],
      ['m', 'Terminator Genisys', 2015],
      ['m', 'Terminator: Dark Fate', 2019],
    ],
  },
  {
    key: 'matrix', name: 'The Matrix', group: 'Sci-fi', order: 'udgivelserne',
    items: [
      ['m', 'The Matrix', 1999],
      ['m', 'The Matrix Reloaded', 2003],
      ['m', 'The Matrix Revolutions', 2003],
      ['m', 'The Matrix Resurrections', 2021],
    ],
  },
  {
    key: 'bttf', name: 'Back to the Future', group: 'Sci-fi', order: 'udgivelserne',
    items: [
      ['m', 'Back to the Future', 1985],
      ['m', 'Back to the Future Part II', 1989, 'Back to the Future 2'],
      ['m', 'Back to the Future Part III', 1990, 'Back to the Future 3'],
    ],
  },
  // ---------------- Fantasy & eventyr ----------------
  {
    key: 'potter', name: 'Harry Potter', group: 'Fantasy & eventyr', order: 'historien', note: 'Fantastiske skabninger foregår før Harry Potter.',
    items: [
      ['m', 'Fantastic Beasts and Where to Find Them', 2016],
      ['m', 'Fantastic Beasts: The Crimes of Grindelwald', 2018],
      ['m', 'Fantastic Beasts: The Secrets of Dumbledore', 2022],
      ['m', 'Harry Potter and the Philosopher\'s Stone', 2001, 'Harry Potter and the Sorcerer\'s Stone'],
      ['m', 'Harry Potter and the Chamber of Secrets', 2002],
      ['m', 'Harry Potter and the Prisoner of Azkaban', 2004],
      ['m', 'Harry Potter and the Goblet of Fire', 2005],
      ['m', 'Harry Potter and the Order of the Phoenix', 2007],
      ['m', 'Harry Potter and the Half-Blood Prince', 2009],
      ['m', 'Harry Potter and the Deathly Hallows: Part 1', 2010, 'Harry Potter and the Deathly Hallows Part 1'],
      ['m', 'Harry Potter and the Deathly Hallows: Part 2', 2011, 'Harry Potter and the Deathly Hallows Part 2'],
    ],
  },
  {
    key: 'lotr', name: 'Ringenes Herre', group: 'Fantasy & eventyr', order: 'historien',
    items: [
      ['s', 'The Lord of the Rings: The Rings of Power', 2022, 'The Rings of Power'],
      ['m', 'The Lord of the Rings: The War of the Rohirrim', 2024, 'The War of the Rohirrim'],
      ['m', 'The Hobbit: An Unexpected Journey', 2012],
      ['m', 'The Hobbit: The Desolation of Smaug', 2013],
      ['m', 'The Hobbit: The Battle of the Five Armies', 2014],
      ['m', 'The Lord of the Rings: The Fellowship of the Ring', 2001],
      ['m', 'The Lord of the Rings: The Two Towers', 2002],
      ['m', 'The Lord of the Rings: The Return of the King', 2003],
    ],
  },
  {
    key: 'pirates', name: 'Pirates of the Caribbean', group: 'Fantasy & eventyr', order: 'udgivelserne',
    items: [
      ['m', 'Pirates of the Caribbean: The Curse of the Black Pearl', 2003],
      ['m', 'Pirates of the Caribbean: Dead Man\'s Chest', 2006],
      ['m', 'Pirates of the Caribbean: At World\'s End', 2007],
      ['m', 'Pirates of the Caribbean: On Stranger Tides', 2011],
      ['m', 'Pirates of the Caribbean: Dead Men Tell No Tales', 2017, 'Pirates of the Caribbean: Salazar\'s Revenge'],
    ],
  },
  {
    key: 'indy', name: 'Indiana Jones', group: 'Fantasy & eventyr', order: 'historien',
    items: [
      ['m', 'Indiana Jones and the Temple of Doom', 1984],
      ['m', 'Raiders of the Lost Ark', 1981, 'Indiana Jones and the Raiders of the Lost Ark'],
      ['m', 'Indiana Jones and the Last Crusade', 1989],
      ['m', 'Indiana Jones and the Kingdom of the Crystal Skull', 2008],
      ['m', 'Indiana Jones and the Dial of Destiny', 2023],
    ],
  },
  {
    key: 'jurassic', name: 'Jurassic Park & World', group: 'Fantasy & eventyr', order: 'udgivelserne',
    items: [
      ['m', 'Jurassic Park', 1993],
      ['m', 'The Lost World: Jurassic Park', 1997],
      ['m', 'Jurassic Park III', 2001, 'Jurassic Park 3'],
      ['m', 'Jurassic World', 2015],
      ['m', 'Jurassic World: Fallen Kingdom', 2018],
      ['m', 'Jurassic World Dominion', 2022, 'Jurassic World: Dominion'],
      ['m', 'Jurassic World Rebirth', 2025, 'Jurassic World: Rebirth'],
    ],
  },
  // ---------------- Action ----------------
  {
    key: 'bond', name: 'James Bond 007', group: 'Action', order: 'udgivelserne',
    items: [
      ['m', 'Dr. No', 1962], ['m', 'From Russia with Love', 1963], ['m', 'Goldfinger', 1964], ['m', 'Thunderball', 1965],
      ['m', 'You Only Live Twice', 1967], ['m', 'On Her Majesty\'s Secret Service', 1969], ['m', 'Diamonds Are Forever', 1971],
      ['m', 'Live and Let Die', 1973], ['m', 'The Man with the Golden Gun', 1974], ['m', 'The Spy Who Loved Me', 1977],
      ['m', 'Moonraker', 1979], ['m', 'For Your Eyes Only', 1981], ['m', 'Octopussy', 1983], ['m', 'A View to a Kill', 1985],
      ['m', 'The Living Daylights', 1987], ['m', 'Licence to Kill', 1989, 'License to Kill'], ['m', 'GoldenEye', 1995],
      ['m', 'Tomorrow Never Dies', 1997], ['m', 'The World Is Not Enough', 1999], ['m', 'Die Another Day', 2002],
      ['m', 'Casino Royale', 2006], ['m', 'Quantum of Solace', 2008], ['m', 'Skyfall', 2012], ['m', 'Spectre', 2015], ['m', 'No Time to Die', 2021],
    ],
  },
  {
    key: 'mi', name: 'Mission: Impossible', group: 'Action', order: 'udgivelserne',
    items: [
      ['m', 'Mission: Impossible', 1996],
      ['m', 'Mission: Impossible 2', 2000, 'Mission: Impossible II', 'M:I-2'],
      ['m', 'Mission: Impossible III', 2006, 'Mission: Impossible 3'],
      ['m', 'Mission: Impossible - Ghost Protocol', 2011],
      ['m', 'Mission: Impossible - Rogue Nation', 2015],
      ['m', 'Mission: Impossible - Fallout', 2018],
      ['m', 'Mission: Impossible - Dead Reckoning Part One', 2023, 'Mission: Impossible - Dead Reckoning'],
      ['m', 'Mission: Impossible - The Final Reckoning', 2025],
    ],
  },
  {
    key: 'fast', name: 'Fast & Furious', group: 'Action', order: 'historien', note: 'Tokyo Drift foregår efter Fast & Furious 6.',
    items: [
      ['m', 'The Fast and the Furious', 2001],
      ['m', '2 Fast 2 Furious', 2003],
      ['m', 'Fast & Furious', 2009, 'Fast and Furious'],
      ['m', 'Fast Five', 2011, 'Fast & Furious 5'],
      ['m', 'Fast & Furious 6', 2013, 'Furious 6'],
      ['m', 'The Fast and the Furious: Tokyo Drift', 2006, 'Tokyo Drift'],
      ['m', 'Furious 7', 2015, 'Fast & Furious 7'],
      ['m', 'The Fate of the Furious', 2017, 'Fast & Furious 8'],
      ['m', 'Fast & Furious Presents: Hobbs & Shaw', 2019, 'Hobbs & Shaw', 'Hobbs and Shaw'],
      ['m', 'F9', 2021, 'F9: The Fast Saga', 'Fast & Furious 9'],
      ['m', 'Fast X', 2023],
    ],
  },
  {
    key: 'wick', name: 'John Wick', group: 'Action', order: 'historien',
    items: [
      ['s', 'The Continental: From the World of John Wick', 2023, 'The Continental'],
      ['m', 'John Wick', 2014],
      ['m', 'John Wick: Chapter 2', 2017],
      ['m', 'John Wick: Chapter 3 - Parabellum', 2019],
      ['m', 'From the World of John Wick: Ballerina', 2025, 'Ballerina'],
      ['m', 'John Wick: Chapter 4', 2023],
    ],
  },
  {
    key: 'diehard', name: 'Die Hard', group: 'Action', order: 'udgivelserne',
    items: [
      ['m', 'Die Hard', 1988],
      ['m', 'Die Hard 2', 1990, 'Die Hard 2: Die Harder'],
      ['m', 'Die Hard with a Vengeance', 1995],
      ['m', 'Live Free or Die Hard', 2007, 'Die Hard 4.0'],
      ['m', 'A Good Day to Die Hard', 2013],
    ],
  },
  // ---------------- Familie & animation ----------------
  {
    key: 'pixar', name: 'Pixar', group: 'Familie & animation', order: 'udgivelserne',
    items: [
      ['m', 'Toy Story', 1995], ['m', 'A Bug\'s Life', 1998], ['m', 'Toy Story 2', 1999], ['m', 'Monsters, Inc.', 2001, 'Monsters Inc'],
      ['m', 'Finding Nemo', 2003], ['m', 'The Incredibles', 2004], ['m', 'Cars', 2006], ['m', 'Ratatouille', 2007],
      ['m', 'WALL·E', 2008, 'WALL-E', 'WALL E'], ['m', 'Up', 2009], ['m', 'Toy Story 3', 2010], ['m', 'Cars 2', 2011], ['m', 'Brave', 2012],
      ['m', 'Monsters University', 2013], ['m', 'Inside Out', 2015], ['m', 'The Good Dinosaur', 2015], ['m', 'Finding Dory', 2016],
      ['m', 'Cars 3', 2017], ['m', 'Coco', 2017], ['m', 'Incredibles 2', 2018], ['m', 'Toy Story 4', 2019], ['m', 'Onward', 2020],
      ['m', 'Soul', 2020], ['m', 'Luca', 2021], ['m', 'Turning Red', 2022], ['m', 'Lightyear', 2022], ['m', 'Elemental', 2023],
      ['m', 'Inside Out 2', 2024], ['m', 'Elio', 2025], ['m', 'Toy Story 5', 2026],
    ],
  },
  {
    key: 'disney', name: 'Disney-klassikere', group: 'Familie & animation', order: 'udgivelserne',
    items: [
      ['m', 'Snow White and the Seven Dwarfs', 1937], ['m', 'Pinocchio', 1940], ['m', 'Dumbo', 1941], ['m', 'Bambi', 1942],
      ['m', 'Cinderella', 1950], ['m', 'Alice in Wonderland', 1951], ['m', 'Peter Pan', 1953], ['m', 'Lady and the Tramp', 1955],
      ['m', 'Sleeping Beauty', 1959], ['m', 'One Hundred and One Dalmatians', 1961, '101 Dalmatians'], ['m', 'The Jungle Book', 1967],
      ['m', 'The Aristocats', 1970], ['m', 'Robin Hood', 1973], ['m', 'The Little Mermaid', 1989], ['m', 'Beauty and the Beast', 1991],
      ['m', 'Aladdin', 1992], ['m', 'The Lion King', 1994], ['m', 'Pocahontas', 1995], ['m', 'The Hunchback of Notre Dame', 1996],
      ['m', 'Hercules', 1997], ['m', 'Mulan', 1998], ['m', 'Tarzan', 1999], ['m', 'Lilo & Stitch', 2002, 'Lilo and Stitch'],
      ['m', 'Tangled', 2010], ['m', 'Frozen', 2013], ['m', 'Big Hero 6', 2014], ['m', 'Zootopia', 2016, 'Zootropolis'], ['m', 'Moana', 2016, 'Vaiana'],
      ['m', 'Frozen II', 2019, 'Frozen 2'], ['m', 'Encanto', 2021], ['m', 'Wish', 2023], ['m', 'Moana 2', 2024, 'Vaiana 2'], ['m', 'Zootopia 2', 2025, 'Zootropolis 2'],
    ],
  },
  {
    key: 'shrek', name: 'Shrek', group: 'Familie & animation', order: 'udgivelserne',
    items: [
      ['m', 'Shrek', 2001], ['m', 'Shrek 2', 2004], ['m', 'Shrek the Third', 2007, 'Shrek 3'], ['m', 'Shrek Forever After', 2010, 'Shrek 4'],
      ['m', 'Puss in Boots', 2011], ['m', 'Puss in Boots: The Last Wish', 2022],
    ],
  },
  {
    key: 'iceage', name: 'Ice Age', group: 'Familie & animation', order: 'udgivelserne',
    items: [
      ['m', 'Ice Age', 2002], ['m', 'Ice Age: The Meltdown', 2006], ['m', 'Ice Age: Dawn of the Dinosaurs', 2009],
      ['m', 'Ice Age: Continental Drift', 2012], ['m', 'Ice Age: Collision Course', 2016], ['m', 'The Ice Age Adventures of Buck Wild', 2022],
    ],
  },
];

// Påskedag (gregoriansk, "anonym" algoritme)
function easter(y) {
  const a = y % 19, b = Math.floor(y / 100), c = y % 100, d = Math.floor(b / 4), e = b % 4, f = Math.floor((b + 8) / 25), g = Math.floor((b - f + 1) / 3);
  const h = (19 * a + b - d - g + 15) % 30, i = Math.floor(c / 4), k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = Math.floor((a + 11 * h + 22 * l) / 451);
  const month = Math.floor((h + l - 7 * m + 114) / 31), day = ((h + l - 7 * m + 114) % 31) + 1;
  return new Date(y, month - 1, day);
}
const md = (m, d) => m * 100 + d;

// Sæsontemaer: vises kun i deres periode (when(dato) → true/false)
const SEASONS = [
  {
    key: 'halloween', name: 'Halloween', order: 'blandet', when: d => md(d.getMonth() + 1, d.getDate()) >= md(10, 1) && md(d.getMonth() + 1, d.getDate()) <= md(11, 2),
    note: 'Uhyggeligt for hele familien – og lidt mere for de voksne.',
    items: [
      ['m', 'Hocus Pocus', 1993], ['m', 'Hocus Pocus 2', 2022], ['m', 'The Nightmare Before Christmas', 1993, 'Tim Burton\'s The Nightmare Before Christmas'],
      ['m', 'Coraline', 2009], ['m', 'Corpse Bride', 2005, 'Tim Burton\'s Corpse Bride'], ['m', 'Casper', 1995], ['m', 'Monster House', 2006],
      ['m', 'Hotel Transylvania', 2012], ['m', 'The Addams Family', 1991], ['m', 'Addams Family Values', 1993],
      ['m', 'Beetlejuice', 1988], ['m', 'Beetlejuice Beetlejuice', 2024], ['m', 'Ghostbusters', 1984], ['m', 'Ghostbusters II', 1989, 'Ghostbusters 2'],
      ['m', 'Ghostbusters: Afterlife', 2021], ['m', 'Ghostbusters: Frozen Empire', 2024], ['m', 'Halloween', 1978], ['m', 'Scream', 1996],
      ['m', 'The Shining', 1980], ['m', 'It', 2017], ['m', 'A Quiet Place', 2018],
    ],
  },
  {
    key: 'paaske', name: 'Påske', order: 'blandet', when: d => { const e = easter(d.getFullYear()); const t = new Date(d.getFullYear(), d.getMonth(), d.getDate()); return t >= new Date(e - 14 * 864e5) && t <= new Date(+e + 864e5); },
    note: 'Påskeharer, chokolade og eventyr.',
    items: [
      ['m', 'Hop', 2011], ['m', 'Peter Rabbit', 2018], ['m', 'Peter Rabbit 2: The Runaway', 2021, 'Peter Rabbit 2'], ['m', 'Rise of the Guardians', 2012],
      ['m', 'Charlie and the Chocolate Factory', 2005], ['m', 'Willy Wonka & the Chocolate Factory', 1971, 'Willy Wonka and the Chocolate Factory'],
      ['m', 'Wonka', 2023], ['m', 'The Prince of Egypt', 1998], ['m', 'Zootopia', 2016, 'Zootropolis'], ['m', 'Paddington', 2014], ['m', 'Paddington 2', 2017],
    ],
  },
  {
    key: 'sommer', name: 'Sommerfilm', order: 'blandet', when: d => md(d.getMonth() + 1, d.getDate()) >= md(6, 15) && md(d.getMonth() + 1, d.getDate()) <= md(8, 20),
    note: 'Sol, strand og sommerferie.',
    items: [
      ['m', 'Mamma Mia!', 2008, 'Mamma Mia'], ['m', 'Mamma Mia! Here We Go Again', 2018], ['m', 'Grease', 1978], ['m', 'Dirty Dancing', 1987],
      ['m', 'The Parent Trap', 1998], ['m', 'Luca', 2021], ['m', 'Moana', 2016, 'Vaiana'], ['m', 'Lilo & Stitch', 2002, 'Lilo and Stitch'],
      ['m', 'Jaws', 1975], ['m', 'Top Gun', 1986], ['m', 'Top Gun: Maverick', 2022], ['m', 'Grown Ups', 2010], ['m', 'The Sandlot', 1993],
      ['m', 'We\'re the Millers', 2013], ['m', 'Blue Crush', 2002],
    ],
  },
  {
    key: 'oscar', name: 'Oscar-vindere', order: 'nyeste først', when: d => d.getMonth() === 1 || d.getMonth() === 2,
    note: 'Årets bedste film ifølge Oscar-uddelingen – nyeste først.',
    items: [
      ['m', 'Anora', 2024], ['m', 'Oppenheimer', 2023], ['m', 'Everything Everywhere All at Once', 2022], ['m', 'CODA', 2021], ['m', 'Nomadland', 2020],
      ['m', 'Parasite', 2019], ['m', 'Green Book', 2018], ['m', 'The Shape of Water', 2017], ['m', 'Moonlight', 2016], ['m', 'Spotlight', 2015],
      ['m', 'Birdman', 2014, 'Birdman or (The Unexpected Virtue of Ignorance)'], ['m', '12 Years a Slave', 2013], ['m', 'Argo', 2012], ['m', 'The Artist', 2011],
      ['m', 'The King\'s Speech', 2010], ['m', 'The Hurt Locker', 2008], ['m', 'Slumdog Millionaire', 2008], ['m', 'No Country for Old Men', 2007],
      ['m', 'The Departed', 2006], ['m', 'Million Dollar Baby', 2004], ['m', 'Gladiator', 2000], ['m', 'Titanic', 1997], ['m', 'Forrest Gump', 1994],
      ['m', 'Schindler\'s List', 1993], ['m', 'The Silence of the Lambs', 1991],
    ],
  },
];

/** Julekalenderen er også et sæsontema (vises 1. november – 6. januar). */
const julSeason = d => d.getMonth() >= 10 || (d.getMonth() === 0 && d.getDate() <= 6);

module.exports = { GROUPS, UNIVERSES, SEASONS, julSeason, easter };
