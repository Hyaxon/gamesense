# Xander Meadows
# Functions to convert JSON data to Glicko-2 ratings and updates

import json
from glicko2 import *

with open("Schedule.json", "r") as file:
    data = json.load(file)

"""
# Test: Print names of all teams
for team in data["teams"]:
    print(team["name"])
"""

"""
# Test: Print all games for each team
for game in team["schedule"]:
    if game["outcome"] != "":
        print(
            game["timestamp"],
            game["opponent"],
            game["outcome"]
        )
"""
        
# Team object
class Team:
    def __init__(self, name):
        self.name = name # Team name

        self.rating = 1500 # Initial Glicko-2 rating
        self.rd = 350 # Initial rating deviation
        self.volatility = 0.06 # Initial volatility

"""
# Test: Create a team and print its initial Glicko-2 ratings
team = Team("Michigan State Spartans")
print(team.name)
print(team.rating)
print(team.rd)
print(team.volatility)
"""

# Make a team object for each team in the JSON data
team_objects = []
for team in data["teams"]:
    team_obj = Team(team["name"])
    team_objects.append(team_obj)

"""
# Test: Print all created team objects and their initial Glicko-2 ratings
for team_obj in team_objects:
    print(team_obj.name)
    print(team_obj.rating)
    print(team_obj.rd)
    print(team_obj.volatility)
    print()  # Add a blank line between teams for readability
"""

