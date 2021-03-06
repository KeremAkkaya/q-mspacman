/*
 *  Ms. Pac-Man Framework
 *
 *  Created by Luuk Bom & Ruud Henken under supervision by Marco Wiering,
 *  Department of Artificial Intelligence, University of Groningen.
 *
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 *
 *  PACMAN
 *  This is the main class which loads the neural networks and environment,
 *  and initiates the game and training.
 *
 */

package pacman;

import environment.Environment;
import global.Globals;
import gui.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Random;
import util.Entrapmentfinder;
import util.Ghostfinder;
import util.Pillfinder;
import util.util;

public class PacMan {

    private static String scoreFile;                // Score from each run will be written to this file
    private static String NNDir;                    // Neural network files will be written to this directory
    private static String NNFile;                   // Resulting NN will be written to this file
    private static String NNSDir;                   // Neural network files for training states will be written to this directory
    
    private static boolean firstepoch = true;       // Is this the first epoch?
    private static int epochBegin = 0;              // At what epoch did the training begin/resume?
    
    private static double explorationchance;        // Current chance of exploration
    private static double learningRate;             // Current learning rate
    
    private static short curAction;                 // Action as selected by network to be taken next
    private static short boldAction;                // Action currently used in exploration
    private static short actualMove;                // Action that was actually performed
    private static int lastNet = -1;                // Network last used in decision making
    private static boolean reverseAction = false;   // Is the new action the reverse of the previous action?
   
    private static double[] curQs = new double[4];  // Current Q-values
    private static double[] newQs = new double[4];  // Future Q-values after selected action (curAction)
    private static double[] newV = new double[1];   // Future V-values after selected action (curAction)
    
    private static float lifeTime = 0;              // How great of a distance Ms.Pac-Man traveled during current game (score for avoiding ghosts)
    private static float totalReward;               // Total reward gathered during current game
    private static double totalPillReward = 0;      // Total reward gathered from collection pills during current game
    
    private static float minReward = 0;             // Variable for plotting performance
    private static float maxReward = 0;             // Variable for plotting performance
    private static float avgReward = 0;             // Variable for plotting performance
    private static float minLifeTime = 0;           // Variable for plotting performance
    private static float maxLifeTime = 0;           // Variable for plotting performance
    private static float avgLifeTime = 0;           // Variable for plotting performance
    private static float minScore = 0;              // Variable for plotting performance
    private static float maxScore = 0;              // Variable for plotting performance
    private static float avgScore = 0;              // Variable for plotting performance
    private static int currentResolution = 0;       // Variable for plotting performance
    
    private static int runcount = 0;                // Amount of network runs during entire training
    private static int gamesdone = -1;              // Amount of games done during entire training
    private static int numSuccess = 0;              // Amount of successful games during entire training
    
    private static double pillPercentage;           // Percentage of completion for current game
    private static int curMaze;                     // Index of current maze
            
    private static int numInputValues;              // Amount of input values in total
    
    private static int inputs;                      // Amount of input nodes per network
    private static int hiddens;                     // Amount of hidden nodes per network
    private static int outputs;                     // Amount of output nodes per network
    
    private static boolean entrapped = false;       // Whether Ms. Pac-Man has chance of escape
    private static int remainPowerpill = 0;         // The amount of network runs any powerpills have left

    private static double ghostThreshold;           // Counter deciding current phase of ghost behaviour
    
    private static Charter chart = null;
    private static RateGraph rgraph = null;
    private static RIPGraph dgraph = null;
    private static QGraph qgraph = null;
    private static double[] visionMemory;
    private static double[] visionDecline;
    
    private static boolean counterReset = false;     // Should all counters be reset?

    // Function to initialize or reset all counters
    public static void resetCounters() {
        minReward = 0;
        maxReward = 0;
        avgReward = 0;
        minLifeTime = 0;
        maxLifeTime = 0;
        avgLifeTime = 0;
        minScore = 0;
        maxScore = 0;
        avgScore = 0;
        currentResolution = 0;
        firstepoch = true;

        counterReset = false;
    }

    // Main function triggered when the program is launched
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("MS. PAC-MAN FRAMEWORK");
        
        
        // Do some basic checks on the validity of the values in the Globals
        Globals.checkValidity();

        // Read the mazes
        String dir = "data/maze/";
        ArrayList<String> mazeList = readMazes(dir);
        int numMazes = mazeList.size();

        // Prepare all output files
        String timestamp = new SimpleDateFormat("yyyyMMdd_HH.mm.ss").format(new Date());
        scoreFile = "data/score/Score_" + timestamp + ".txt";
        NNDir = "data/NN/";
        NNFile = "NN_" + timestamp + ".txt";
        NNSDir = "data/NNS/";
        
        System.out.println("SIMULATION #" + timestamp + ":");

        // Set ghosts to a certain state if a static ghost state is ordered by the Globals
        if (Globals.alwaysEnabled == true) {
            Globals.startingGhostState = Globals.alwaysState;
        }

        // Initialize a list of neural networks for training state-action pairs
        ArrayList<NeuralNetwork> netList = new ArrayList<NeuralNetwork>();
        
        // Calculate the amount of input values (in total, not per network!)
        numInputValues = util.calculateActiveAlgorithms() * 4;
        
        // Calculate the specifications of the networks
        // Customizing these values will require altering of many functions and variables,
        // mainly those used for training and collecting input.
        hiddens = Globals.hiddenNeurons;
        outputs = 1;

        // Initialize each network
        if (Globals.activeNet == Globals.NETWORK_SINGLE) {
            inputs = numInputValues / 4;
            
            NeuralNetwork netSingle = new NeuralNetwork(inputs,hiddens,outputs);
            netList.add(netSingle);
        } else {
            // Alter the amount of inputs if we are dealing with action networks according to the Globals
            inputs = numInputValues;
            
            NeuralNetwork netLeft = new NeuralNetwork(inputs, hiddens, outputs);
            NeuralNetwork netRight = new NeuralNetwork(inputs, hiddens, outputs);
            NeuralNetwork netUp = new NeuralNetwork(inputs, hiddens, outputs);
            NeuralNetwork netDown = new NeuralNetwork(inputs, hiddens, outputs);
            netList.add(netLeft);
            netList.add(netRight);
            netList.add(netUp);
            netList.add(netDown);
        }

        // Initialize a list of neural networks for training states
        ArrayList<NeuralNetwork> netListForStates = new ArrayList<NeuralNetwork>();

        // When QV-learning is enabled, create neural networks for training states
        if (Globals.learningRule == Globals.QV) {
            NeuralNetwork netStates = new NeuralNetwork(numInputValues, hiddens, outputs);
            netListForStates.add(netStates);
        }

        // Load a predefined file if ordered to do so by the Globals
        // This will overwrite some of the Global-settings
        if (Globals.loadFile != "") {
            netList = readNN(NNDir + Globals.loadFile);
            netListForStates = readNN(NNSDir + Globals.loadFile);
        }

        // Initalize any plots enabled in the Globals
        if (Globals.showVisualisation == true && Globals.showCharter == true) {
            chart = new Charter("Ms. Pac-man - Charter");
        }
        if (Globals.showRateGraph == true) {
            rgraph = new RateGraph("Ms. Pac-man - RateGraph");
        }
        if (Globals.showQGraph == true) {
            qgraph = new QGraph("Ms. Pac-man - QGraph");
        }
        if (Globals.showRIPGraph == true) {
            dgraph = new RIPGraph("Ms. Pac-man - RIPGraph");
        }

        // Prepare for training if this is enabled in the Globals
        if (Globals.enableTraining == true) {
            Globals.trainingRegimen();
        } else {
            System.out.println("");
        }

        // Initialize some variables
        Random generator = new Random();
        runcount = -1;

        // And start the magic
        do {
            // Continue with a random maze
            generator = new Random();
            curMaze = generator.nextInt(numMazes);
            
            // Satisfy some graphs
            if (Globals.showVisualisation == true && Globals.showCharter == true) {
                chart.clearData();
            }

            // Perform a counter reset if necessary
            counterReset = Globals.trainingData.getTraining(gamesdone);
            if (counterReset == true) {
                resetCounters();
            }

            // Ceate and initialize the game environment
            Environment Game = new Environment(mazeList.get(curMaze));
            initialize(Game);
            double maxScore = Game.pillsLeft * Globals.rewardPill;
            totalReward = 0;
            totalPillReward = 0;
            lifeTime = 0;

            // Reset ghost behaviour
            ghostThreshold = 0;
            Game.globalGhostState = Globals.startingGhostState;

            // Wait for game to load
            while (Game.gameLoaded == false) {
            }

            // Write the current networks to file
            writeNN(NNDir, netList);
            writeNN(NNSDir, netListForStates);

            // Repeat until the game ends
            while (Game.gameEnded == false) {

                // Perform a network run and keep track of its results
                totalReward += run(Game, netList, netListForStates);
                lifeTime += Globals.pacmanSpeed;
                ghostThreshold += Globals.pacmanSpeed;

                if (Game.gameLost == true) {
                    curMaze = 0;
                }
                
                // Delay the gameplay if we are in realtime visualisation
                if (Globals.showVisualisation == true) {
                    Thread.sleep(1000 / Globals.timeResolution);
                }
                
                // Set ghost behaviour
                determineGhostBehaviour(generator, Game);
            }
            
            // Satisfy some graphs
            if (Globals.showVisualisation == true) {
                Game.vis.getRidOf();
            }
            if (Globals.showQGraph == true) {
                qgraph.clearData();
            }
            
            // If training was delayed until after the game, perform training now
            if (Globals.trainingAfterTheFact == true) {
                Iterator<NeuralNetwork> it = netList.iterator();
                while(it.hasNext())
                {
                    NeuralNetwork obj = it.next();
                    obj.process_queue(Game.gameLost);
                }
            }
            
        } while (Globals.limitGames == false || Globals.totalGames > gamesdone);
        
        System.exit(0);
    }

    // Function to determine the proper ghost behaviour
    public static void determineGhostBehaviour(Random generator, Environment Game) {

        // Override ghost behaviour if a static ghost state was set in the Globals
        if (Globals.alwaysEnabled == true) {
            Game.globalGhostState = Globals.alwaysState;
            
            if (Globals.alwaysState == Globals.GHOST_AFRAID) {
                remainPowerpill = 1;
                for (int i = 0; i < Game.getGhosts().length; i++) {
                    Game.getGhost(i).state = Globals.GHOST_AFRAID;
                }
            }
            
            Game.propagateGhostBehaviour();
            return;
        }

        // Alter the global ghost state
        
        // Remember, the ghosts will only take on this state when propagateGhostBehaviour
        // is called, or their state is manually altered to -1.

        if (ghostThreshold > 1) {
            Game.globalGhostState = Globals.GHOST_CHASE;
        }
        if (ghostThreshold > 10) {
            Game.globalGhostState = Globals.GHOST_RANDOM;
        }
        if (ghostThreshold > 70) {
            Game.globalGhostState = Globals.GHOST_CHASE;
        }
        if (ghostThreshold > 85) {
            Game.globalGhostState = Globals.GHOST_RANDOM;
        }
        if (ghostThreshold >= 120) {
            Game.globalGhostState = Globals.GHOST_SCATTER;
            ghostThreshold = 0;
        }

        // If Ms. Pac-man just ate a powerpill
        if (Game.justPowerpill == true) {

            Game.justPowerpill = false;

            // Set how many network runs the power pill has left
            remainPowerpill = (int) ((double) Globals.durationPowerpill / (double) Globals.pacmanSpeed);

            // Set all ghosts to the afraid state
            for (int i = 0; i < Game.getGhosts().length; i++) {
                Game.getGhost(i).state = Globals.GHOST_AFRAID;
            }

        } else if (remainPowerpill > 0) {

            // If there is a power pill still active, lower the runs it has left.
            remainPowerpill--;

        } else {

            // If there is no power pill active, propagate the global ghost state to all ghosts
            // They will assume the global ghost state after this move
            boolean dontbeScared = false;
            for (int i = 0; i < Game.getGhosts().length; i++) {
                if (Game.getGhost(i).state == Globals.GHOST_AFRAID) {
                    dontbeScared = true;
                }
            }
            if (dontbeScared == true) {
                Game.propagateGhostBehaviour();
            }

        }
    }

    // Function to read all mazes in a directory
    public static ArrayList<String> readMazes(String dir) {
        ArrayList<String> mazeList = new ArrayList<String>();

        File files = new File(dir);
        for (File f : files.listFiles()) {
            if (f.isFile()) {
                mazeList.add(f.toString());
            }
        }
        return mazeList;
    }

    // Function to preprocess the game environment for the path finding algorithms
    public static void initialize(Environment Game) {
        Game.setVertices();
        Game.setNeigborVertices();
    }

    // Function to get the state representation in a certain Game
    // This is a collection of all input values
    // Depending on drawGraph the state representation will be plotted, if allowed by the Globals
    public static double[] getStateRep(Environment Game, boolean drawGraph) {
        // Initialize some variables
        double[] stateRep = new double[numInputValues];
        Ghostfinder gh = null;
        Pillfinder pf = null;
        int x1 = Game.getPacMan().getX1();
        int y1 = Game.getPacMan().getY1();

        // Get possible actions
        ArrayList<Short> pa = Game.possibleActions(x1, y1);

        // If input algorithms require the ghostFinder, let it do its thing
        if (Globals.enableGhostDistanceInput || Globals.enableGhostDirectionInput || Globals.enableGhostAfraidInput) {
            gh = new Ghostfinder();
            gh.findGhosts(Game);
        }
        // If input algorithms require the Pillfinder, let it do its thing
        if (Globals.enablePillInput || Globals.enablePowerPillInput) {
            pf = new Pillfinder();
            pf.findPills(x1, y1, Game);
        }

        // Let the entrapment finder find entrapment, and store the result
        Entrapmentfinder f = new Entrapmentfinder();
        f.findEntrapment((short) x1, (short) y1, Game.getGhosts(), Game);
        if (f.safeFound == true) {
            entrapped = false;
        } else {
            entrapped = true;
        }

        // Initialize categorization of ghosts
        Agent[] ghostAll = Game.getGhosts();
        Agent[] ghostAfraid;
        Agent[] ghostNotAfraid;
        int afraidCount = 0;
        int notafraidCount = 0;

        // Count the amount of afraid and non-afraid ghosts
        for (int i = 0; i < ghostAll.length; i++) {
            // Ignore any ghosts which are currently out of the game
            if (ghostAll[i].getWaitTime() == 0) {
                if (ghostAll[i].state == Globals.GHOST_AFRAID) {
                    afraidCount++;
                } else {
                    notafraidCount++;
                }
            }
        }

        ghostAfraid = new Agent[afraidCount];
        ghostNotAfraid = new Agent[notafraidCount];

        // Sort ghosts based on whether they are afraid or not
        for (int i = 0; i < ghostAll.length; i++) {
            // And once more ignore any ghosts which are out of the game
            if (ghostAll[i].getWaitTime() == 0) {
                if (ghostAll[i].state == Globals.GHOST_AFRAID) {
                    ghostAfraid[afraidCount - 1] = ghostAll[i];
                    afraidCount--;
                } else {
                    ghostNotAfraid[notafraidCount - 1] = ghostAll[i];
                    notafraidCount--;
                }
            }
        }

        // Collect a huge array of input values, by walking through each input algorithm
        // If asked to do so, the values will be plotted as well
        int i = 0;
        if (Globals.enableActionInput == true) {
            stateRep[i + 0] = actualMove == Globals.ACTION_LEFT ? 1 : 0;
            stateRep[i + 1] = actualMove == Globals.ACTION_RIGHT ? 1 : 0;
            stateRep[i + 2] = actualMove == Globals.ACTION_UP ? 1 : 0;
            stateRep[i + 3] = actualMove == Globals.ACTION_DOWN ? 1 : 0;

            if (drawGraph==true && Globals.showVisualisation == true && Globals.showCharter == true) {
                chart.alterData("ActionInput", "Left", stateRep[i + 0]);
                chart.alterData("ActionInput", "Right", stateRep[i + 1]);
                chart.alterData("ActionInput", "Up", stateRep[i + 2]);
                chart.alterData("ActionInput", "Down", stateRep[i + 3]);
            }
            i += 4;
        }
        if (Globals.enablePillInput == true) {
            stateRep[i + 0] = pf.toLeft;
            stateRep[i + 1] = pf.toRight;
            stateRep[i + 2] = pf.toUp;
            stateRep[i + 3] = pf.toDown;

            if (drawGraph==true && Globals.showVisualisation == true && Globals.showCharter == true) {
                chart.alterData("PillInput", "Left", stateRep[i + 0]);
                chart.alterData("PillInput", "Right", stateRep[i + 1]);
                chart.alterData("PillInput", "Up", stateRep[i + 2]);
                chart.alterData("PillInput", "Down", stateRep[i + 3]);
            }
            i += 4;

        }
        if (Globals.enableGhostDistanceInput == true) {
            gh.calculateDistance(ghostNotAfraid);

            stateRep[i + 0] = gh.toLeft;
            stateRep[i + 1] = gh.toRight;
            stateRep[i + 2] = gh.toUp;
            stateRep[i + 3] = gh.toDown;

            if (drawGraph==true && Globals.showVisualisation == true && Globals.showCharter == true) {
                chart.alterData("GhostDistanceInput", "Left", stateRep[i + 0]);
                chart.alterData("GhostDistanceInput", "Right", stateRep[i + 1]);
                chart.alterData("GhostDistanceInput", "Up", stateRep[i + 2]);
                chart.alterData("GhostDistanceInput", "Down", stateRep[i + 3]);
            }
            i += 4;

        }
        if (Globals.enableGhostDirectionInput == true) {
            gh.calculateDirection(ghostNotAfraid);

            stateRep[i + 0] = gh.dirLeft;
            stateRep[i + 1] = gh.dirRight;
            stateRep[i + 2] = gh.dirUp;
            stateRep[i + 3] = gh.dirDown;

            if (drawGraph==true && Globals.showVisualisation == true && Globals.showCharter == true) {
                chart.alterData("GhostDirectionInput", "Left", stateRep[i + 0]);
                chart.alterData("GhostDirectionInput", "Right", stateRep[i + 1]);
                chart.alterData("GhostDirectionInput", "Up", stateRep[i + 2]);
                chart.alterData("GhostDirectionInput", "Down", stateRep[i + 3]);
            }
            i += 4;

        }
        if (Globals.enableGhostAfraidInput == true) {
            gh.calculateDirection(ghostAfraid);

            stateRep[i + 0] = gh.dirLeft;
            stateRep[i + 1] = gh.dirRight;
            stateRep[i + 2] = gh.dirUp;
            stateRep[i + 3] = gh.dirDown;

            if (drawGraph==true && Globals.showVisualisation == true && Globals.showCharter == true) {
                chart.alterData("GhostAfraidInput", "Left", stateRep[i + 0]);
                chart.alterData("GhostAfraidInput", "Right", stateRep[i + 1]);
                chart.alterData("GhostAfraidInput", "Up", stateRep[i + 2]);
                chart.alterData("GhostAfraidInput", "Down", stateRep[i + 3]);
            }
            i += 4;

        }
        if (Globals.enableEntrapmentInput == true) {

            stateRep[i + 0] = f.safeDegree[0];
            stateRep[i + 1] = f.safeDegree[1];
            stateRep[i + 2] = f.safeDegree[2];
            stateRep[i + 3] = f.safeDegree[3];

            if (drawGraph==true && Globals.showVisualisation == true && Globals.showCharter == true) {
                chart.alterData("EntrapmentInput", "Left", stateRep[i + 0]);
                chart.alterData("EntrapmentInput", "Right", stateRep[i + 1]);
                chart.alterData("EntrapmentInput", "Up", stateRep[i + 2]);
                chart.alterData("EntrapmentInput", "Down", stateRep[i + 3]);
            }
            i += 4;

        }
        if (Globals.enablePowerPillInput == true) {
            stateRep[i + 0] = pf.toLeftPower;
            stateRep[i + 1] = pf.toRightPower;
            stateRep[i + 2] = pf.toUpPower;
            stateRep[i + 3] = pf.toDownPower;

            if (drawGraph==true && Globals.showVisualisation == true && Globals.showCharter == true) {
                chart.alterData("PowerPillInput", "Left", stateRep[i + 0]);
                chart.alterData("PowerPillInput", "Right", stateRep[i + 1]);
                chart.alterData("PowerPillInput", "Up", stateRep[i + 2]);
                chart.alterData("PowerPillInput", "Down", stateRep[i + 3]);
            }
            i += 4;

        }
        if (Globals.enablePillsEatenInput == true) {
            double pl = Math.sqrt(1.0 - (double) Game.pillsLeft / (double) Game.totalPills);

            stateRep[i + 0] = pl;
            stateRep[i + 1] = pl;
            stateRep[i + 2] = pl;
            stateRep[i + 3] = pl;

            if (drawGraph==true && Globals.showVisualisation == true && Globals.showCharter == true) {
                chart.alterData("PillsEatenInput", "Left", stateRep[i + 0]);
                chart.alterData("PillsEatenInput", "Right", stateRep[i + 1]);
                chart.alterData("PillsEatenInput", "Up", stateRep[i + 2]);
                chart.alterData("PillsEatenInput", "Down", stateRep[i + 3]);
            }
            i += 4;

        }
        if (Globals.enablePowerPillDurationInput == true) {
            // Acquire the time any powerpills will still be active
            short numActiveGhosts = 0;
            double remainingTimePowerPill;

            for (int j = 0; j < Game.numGhosts; j++) {
                short ghostState = Game.getGhost(j).getState();
                if (ghostState != Globals.GHOST_AFRAID) {
                    numActiveGhosts++;
                }
            }

            if (numActiveGhosts == Game.numGhosts) {
                remainingTimePowerPill = 0.0;
            } else {
                remainingTimePowerPill = (double) remainPowerpill / (double) ((double) Globals.durationPowerpill / (double) Globals.pacmanSpeed);
            }
            stateRep[i + 0] = remainingTimePowerPill;
            stateRep[i + 1] = remainingTimePowerPill;
            stateRep[i + 2] = remainingTimePowerPill;
            stateRep[i + 3] = remainingTimePowerPill;

            if (drawGraph==true && Globals.showVisualisation == true && Globals.showCharter == true) {
                chart.alterData("PowerPillDurationInput", "Left", stateRep[i + 0]);
                chart.alterData("PowerPillDurationInput", "Right", stateRep[i + 1]);
                chart.alterData("PowerPillDurationInput", "Up", stateRep[i + 2]);
                chart.alterData("PowerPillDurationInput", "Down", stateRep[i + 3]);
            }
            i += 4;

        }

        // Return the resulting state representation
        return stateRep;
    }

    // This function performs the actual network runs,
    // including collecting input, propagating the nodes, performing the action and training the network
    public static double run(Environment Game, ArrayList<NeuralNetwork> netList, ArrayList<NeuralNetwork> netListForStates) {
        double reward;
        runcount++;

        // Get the current state representation
        double[] curStateRep = getStateRep(Game, true);

        // Compute the current Q-values and plot them if told so by the Globals
        curQs = getQValues(curStateRep, netList);
        if (Globals.showQGraph == true) {
            for (int i = 0; i < 4; i++) {
                qgraph.addQValue(i, runcount, curQs[i]);
            }
        }
        
        // Perform the previously selected move
        actualMove = Game.getPacMan().move(curAction, Game);
        boldAction = actualMove;

        // Update the visualization
        if (Globals.showVisualisation == true) {
            Game.vis.updateVisualisation();
        }
        
        // Determine reward, based on the action just performed
        int PacManX = Game.getPacMan().getX2();
        int PacManY = Game.getPacMan().getY2();
        reward = Game.eatItem(PacManX, PacManY, Game.getPacMan().getMovefloat());
        totalPillReward += reward;
        reward += Globals.rewardStep;
        if (reverseAction == true) reward += Globals.rewardReverse;

        // Check if game is finished, move ghosts, and check again
        reward += checkFinished(Game);
        if (!Game.gameEnded) {
            for (int i = 0; i < Game.getGhosts().length; i++) {
                Game.moveGhost(i);
            }
            reward += checkFinished(Game);
        }
        if (Game.gameLost) {
            reward += Globals.rewardLose;
            
            if (Globals.showRIPGraph) {
                dgraph.plotDeath(lastNet);
            }
        } else if (Game.gameEnded && !Game.gameLost) {
            reward += Globals.rewardWin;
        }

        // Compute the state representation Q-values in the state resulting from the action just performed
        double[] newStateRep = getStateRep(Game, false);
        newQs = getQValues(newStateRep, netList);

        // Get the possible actions in the resulting state
        ArrayList<Short> newPA = Game.possibleActions(Game.getPacMan().getX1(), Game.getPacMan().getY1());

        // Initialize some variables
        short newAction = -1;
        short bestAction = -1;
        double newQsa = newQs[newPA.get(0)];

        // Exploitation vs. Exploration
        // Select the best action and assume this will be the next action
        // Store the result and the corresponding output value
        for (int i = 0; i < newPA.size(); i++) {
            if (newQs[newPA.get(i)] >= newQsa) {
                bestAction = newPA.get(i);
                newAction = newPA.get(i);
                newQsa = newQs[newPA.get(i)];
            }
        }

        // Get the chance Ms. Pac-Man should be forced to explore
        explorationchance = Globals.explorationTime;
        if (Globals.gradualExploration == true && gamesdone >= Globals.explorationTippingpoint) {
            if (gamesdone < Globals.explorationLowest + Globals.explorationTippingpoint) {
                explorationchance = Globals.explorationMinimum + (Globals.explorationTime - Globals.explorationMinimum) * ((double) Globals.explorationLowest - (double) (gamesdone - Globals.explorationTippingpoint)) / (double) Globals.explorationLowest;
            } else {
                explorationchance = Globals.explorationMinimum;
            }
        }

        // Exploration
        // If we need to explore, select a random action and let this be the next action
        if (Math.random() <= explorationchance) {
            // Depending on the globals...
            if (Globals.boldExploration) {
                // Select a random action, keep a steady direction until the next intemrsection
                // and disallow reversing on a path.
                if (Game.getPacMan().reachedNewVertex) {
                    ArrayList<Short> actions = new ArrayList<Short>();
                    for (int i = 0; i < newPA.size(); i++) {
                        short pa = newPA.get(i);
                        if (pa != util.reverseActionFor(actualMove)) {
                            actions.add(pa);
                        }
                    }
                    if (actions.isEmpty()) {
                        actions = newPA;
                    }
                    boldAction = actions.get((int) (Math.random() * actions.size()));
                    
                }
                newAction = boldAction;
            } else {
                // Or just select a random action
                newAction = newPA.get((int) (Math.random() * newPA.size()));
            }
        }

        // Calculate the desired network output, and train network
        // The new action that was just selected has not yet been performed
        
        // Q(v)-LEARNING
        if (Globals.learningRule == Globals.QV) {
            NeuralNetwork netStates = netListForStates.get(0);
            newV = netStates.compute(newStateRep);
            // Get the desired output for the action that was just performed
            // Interpretation of formula:  r + y * V(s')
            curQs[curAction] = reward + (Globals.discountFactor * (Game.gameEnded ? 0.0 : newV[0]));
            // Get the desired state output for the action that was just performed
            double[] desiredStateOutput =  {curQs[curAction]};
            // Train the network
            // Interpretation of formula: V(s) = V(s) + n * (r + y * V(s') - V(s))
            train(netStates, curStateRep, desiredStateOutput);
            
        // SARSA
        } else if (Globals.learningRule == Globals.SARSA) {
            // Get the desired output for the action that was just performed
            // Interpretation of formula: r + y * Q(s', a')
            curQs[curAction] = reward + (Globals.discountFactor * (Game.gameEnded ? 0.0 : newQs[newAction]));
     
        // Q-LEARNING
        } else {
            // Get the desired output for the action that was just performed
            // Interpretation of formula: r + y * max Q(s', a')
            curQs[curAction] = reward + (Globals.discountFactor * (Game.gameEnded ? 0.0 : newQs[bestAction]));
        }

        // Acquire the desired output value
        double[] desiredOutput = {curQs[curAction]};

        // In the case of QV-learning, train the state network
        // Interpretation of formula: Q(s, a) = Q(s, a) + n * (r + y * V(s') - Q(s, a))
        if (Globals.activeNet == Globals.NETWORK_SINGLE) {
            double[] input = getInputValues(curStateRep, curAction);
            train(netList.get(0), input, desiredOutput);
        } else {
            double[] input = curStateRep;
            train(netList.get(curAction), input, desiredOutput);
        }

        // Store if the next action will reverse Ms. Pac-Man on her path
        if (curAction == util.reverseActionFor(newAction)) {
            reverseAction = true;
        } else {
            reverseAction = false;
        }

        // Set the next action that was select as the future action to be performed
        curAction = newAction;
        
        // Lower the learning rate if told by the Globals to do so on a win
        if (Game.gameEnded && !Game.gameLost) {
            Globals.learningRate *= Globals.diminishLearning;
        }

        // Update the visualization
        if (Globals.showVisualisation == true) {
            Game.vis.updateVisualisation();
        }

        return (reward);
    }
    
    // Function that returns whether the game has finished
    private static double checkFinished(Environment Game) {
        // Get whether there has been a collision with a ghost and process the type of collision
        short ghostCollision = Game.ghostCollision();
        if (ghostCollision == Globals.COLLISION_FATAL) {
            // A fatal collision
            Game.gameEnded = true;
            Game.gameLost = true;

            pillPercentage = ((double) (Game.totalPills - Game.pillsLeft)) / (double) Game.totalPills * 100;

            writeScore(lifeTime, Game);
        } else if (ghostCollision == Globals.COLLISION_ALLOWED) {
            // A collision with a scared ghost
            return Globals.rewardGhost;
        } else {
            // And if there has not been a collision, check if Ms.Pac-Man ate all pills
            if (Game.pillsLeft == 0) {
                // Tthe game has been won
                Game.gameEnded = true;
                Game.gameLost = false;
                numSuccess++;

                pillPercentage = 100;
                writeScore(lifeTime, Game);            }
        }

        if (Game.gameEnded == true) {
            remainPowerpill = 0;
        }

        return 0;
    }

    // Function that retrieves the input values associated with a certain action,
    // out of a given state representation
    private static double[] getInputValues(double[] stateRep, short action) {
        double[] input = new double[inputs];

        int inputcursor = action;
        int i = 0;
        while (inputcursor < stateRep.length) {
            input[i] = stateRep[inputcursor];
            i++;
            inputcursor += 4;
        }
        return input;
    }

    // Function that acquires Q-Values by propagating one or more networks based on a state representation
    private static double[] getQValues(double[] stateRep, ArrayList<NeuralNetwork> netList) {
        double[] Qs = new double[4];

        // If we are dealing with a single network, offer input four times to the network.
        // Each time with the input values of a single direction and a single output node,
        // which represents the output value for that direction.
        if (Globals.activeNet == Globals.NETWORK_SINGLE) {
            for (short i = 0; i < 4; i++) {
                double[] out = netList.get(0).compute(getInputValues(stateRep, i));
                Qs[i] = out[0];
            }
        // If we are dealing with action networks, offer input once to each network.
        // Use the entire state representation as input.
        } else {
            for (short i = 0; i < 4; i++) {
                double[] out = netList.get(i).compute(stateRep);
                Qs[i] = out[0];
            }
        }

        return Qs;
    }

    // Function that trains a network based on a given input and a desired output
    private static void train(NeuralNetwork net, double[] input, double[] desired) {
        // Calculate the proper learningRate
        learningRate = Globals.learningRate;
        if (Globals.gradualLearning == true && gamesdone >= Globals.learningTippingpoint) {
            if (gamesdone < Globals.learningLowest + Globals.learningTippingpoint) {
                learningRate = Globals.learningMinimum + (Globals.learningRate - Globals.learningMinimum) * ((double) Globals.learningLowest - (double) (gamesdone - Globals.learningTippingpoint)) / (double) (Globals.learningLowest);
            } else {
                learningRate = Globals.learningMinimum;
            }
        }
        
        // Train now, or wait until after the game is finished if ordered to do so by the Globals
        if (Globals.trainingAfterTheFact == true) {
            net.enqueue(input, desired, learningRate);
        } else {
            net.train(input, desired, learningRate);
        }
    }

    // Function that calculates statistics after a game is finished,
    // plotting and storing them afterwards
    private static void writeScore(float score, Environment Game) {
        gamesdone++;

        if (firstepoch == true) {
            epochBegin = gamesdone;
        }

        currentResolution = Globals.graphResolution;

        int runningAverage = Globals.runningAverage;
        if (runningAverage > gamesdone) {
            runningAverage = gamesdone + 1;
        }

        avgLifeTime = (float) ((1 - 1.0 / (double) runningAverage) * avgLifeTime + 1.0 / (double) runningAverage * lifeTime);

        if (maxLifeTime == 0) {
            maxLifeTime = lifeTime;
        } else {
            maxLifeTime *= 0.9;
            if (lifeTime > maxLifeTime) {
                maxLifeTime = lifeTime;
            }
            if (avgLifeTime > maxLifeTime) {
                maxLifeTime = avgLifeTime;
            }
        }
        if (minLifeTime == 0) {
            minLifeTime = lifeTime;
        } else {
            minLifeTime *= 1.1;
            if (lifeTime < minLifeTime) {
                minLifeTime = lifeTime;
            }
            if (avgLifeTime < minLifeTime) {
                minLifeTime = avgLifeTime;
            }
        }

        if (Globals.showRateGraph == true && Globals.plotLifeTime == true && gamesdone % currentResolution == 0) {
            rgraph.addLifeTime(gamesdone, avgLifeTime, maxLifeTime, minLifeTime);
        }


        avgScore = (float) ((1 - 1.0 / (double) runningAverage) * avgScore + 1.0 / (double) runningAverage * totalPillReward);

        if (maxScore == 0) {
            maxScore = (float) totalPillReward;
        } else {
            maxScore *= maxScore > 0 ? 0.8 : 1.2;
            if (totalPillReward > maxScore) {
                maxScore = (float) totalPillReward;
            }
            if (avgScore > maxScore) {
                maxScore = avgScore;
            }
        }
        if (minScore == 0) {
            minScore = (float) totalPillReward;
        } else {
            minScore *= minScore > 0 ? 1.2 : 0.8;
            if (totalPillReward < minScore) {
                minScore = (float) totalPillReward;
            }
            if (avgScore < minScore) {
                minScore = avgScore;
            }
        }

        double avgCompletion = (double) avgScore / (double) Game.totalPills / (double) Globals.rewardPill * 100;
        double maxCompletion = (double) maxScore / (double) Game.totalPills / (double) Globals.rewardPill * 100;
        double minCompletion = (double) minScore / (double) Game.totalPills / (double) Globals.rewardPill * 100;


        if (Globals.showRateGraph == true && gamesdone % currentResolution == 0) {
            rgraph.addCompletion(gamesdone, avgCompletion, maxCompletion, minCompletion);
        }


        avgReward = (float) ((1 - 1.0 / (double) runningAverage) * avgReward + 1.0 / (double) runningAverage * totalReward);

        if (maxReward == 0) {
            maxReward = totalReward;
        } else {
            maxReward *= maxReward > 0 ? 0.8 : 1.2;
            if (totalReward > maxReward) {
                maxReward = totalReward;
            }
            if (avgScore > maxScore) {
                maxReward = avgReward;
            }
        }
        if (minScore == 0) {
            minReward = totalReward;
        } else {
            minReward *= minReward > 0 ? 1.2 : 0.8;
            if (totalReward < minReward) {
                minReward = totalReward;
            }
            if (avgReward < minReward) {
                minReward = avgReward;
            }
        }

        if (Globals.showRateGraph == true && Globals.plotAbsoluteRewards == true && gamesdone % currentResolution == 0) {
            rgraph.addReward(gamesdone, avgReward, maxReward, minReward);
        }

        firstepoch = false;

        System.out.println("#" + String.format("%05d", gamesdone) + " - lifeTime:  " + String.format("%6s", lifeTime) + "  - Score:  " + String.format("%6.2f", (float)pillPercentage) + "%" + "  - Exploration:  " + String.format("%5.4f", explorationchance) + "  - learningRate:  " + String.format("%8.7f", learningRate));
        
        writeMSE(scoreFile);

    }

    // Function that writes a list of networks to a file in a directory
    private static void writeNN(String dir, ArrayList<NeuralNetwork> net) {
        try {
            PrintWriter out = new PrintWriter(new FileWriter(dir + NNFile));

            // write input settings
            out.println("## INPUT SETTINGS ##");

            String globalVars = "";
            globalVars += Globals.enableActionInput + ",";
            globalVars += Globals.enablePillInput + ",";
            globalVars += Globals.enableGhostDistanceInput + ",";
            globalVars += Globals.enableGhostDirectionInput + ",";
            globalVars += Globals.enableGhostAfraidInput + ",";
            globalVars += Globals.enableEntrapmentInput + ",";
            globalVars += Globals.enablePowerPillInput + ",";
            globalVars += Globals.enablePillsEatenInput + ",";
            globalVars += Globals.enablePowerPillDurationInput + ",";

            out.println(
                    globalVars
                    + avgLifeTime + ","
                    + gamesdone + ","
                    + numSuccess);
            out.println("## NEURAL NETWORKS ##");
            out.flush();

            // write all NN's
            for (int i = 0; i < net.size(); i++) {
                net.get(i).writeNN(dir + NNFile);
            }

            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Function that writes the results of a game to a CSV-file
    public static void writeMSE(String filename) {
        // Format: number of games played, index of maze, percentage of pills collected,
        //         lifetime in distance travelled by Ms. Pac-Man, exploration chance,
        //         learning rate
        

        try {
            PrintWriter out = new PrintWriter(new FileWriter(filename, true));
            out.println(gamesdone + "," + curMaze + "," + pillPercentage + "," + lifeTime + "," + (float)explorationchance + "," + (float)learningRate);
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Function that reads one or more networks from a file
    // This will also override some of the Global-settings
    private static ArrayList<NeuralNetwork> readNN(String filename) {
        ArrayList<NeuralNetwork> net = new ArrayList<NeuralNetwork>();

        double[][] hiddenWeights = null;
        double[][] outputWeights = null;
        double[] hiddenBias = null;
        double[] outputBias = null;

        System.out.println("Reading file '" + filename + "'...");
        try {
            BufferedReader in = new BufferedReader(new FileReader(filename));
            if (in.readLine().startsWith("## INPUT SETTINGS ##")) {
                String settings = in.readLine();
                String[] settingsParts = settings.split(",");
                Globals.enableActionInput = Boolean.parseBoolean(settingsParts[0]);
                Globals.enablePillInput = Boolean.parseBoolean(settingsParts[1]);
                Globals.enableGhostDistanceInput = Boolean.parseBoolean(settingsParts[2]);
                Globals.enableGhostDirectionInput = Boolean.parseBoolean(settingsParts[3]);
                Globals.enableGhostAfraidInput = Boolean.parseBoolean(settingsParts[4]);
                Globals.enableEntrapmentInput = Boolean.parseBoolean(settingsParts[5]);
                Globals.enablePowerPillInput = Boolean.parseBoolean(settingsParts[6]);
                Globals.enablePillsEatenInput = Boolean.parseBoolean(settingsParts[7]);
                Globals.enablePowerPillDurationInput = Boolean.parseBoolean(settingsParts[8]);
                int offset = Globals.numAlghorithms;
                avgLifeTime = Float.parseFloat(settingsParts[offset + 0]);
                gamesdone = Integer.parseInt(settingsParts[offset + 1]);
                numSuccess = Integer.parseInt(settingsParts[offset + 2]);

                // Show input settings to user
                printSettings();
            }

            in.readLine();

            System.out.println("## Neural Networks ##");
            for (int n = 0; n < 4; n++) {
                String firstLine = in.readLine();
                numInputValues = util.calculateActiveAlgorithms() * 4;
                inputs = numInputValues / 4;
                if (Globals.activeNet == Globals.NETWORK_ACTION) {
                    inputs = inputs * 4;
                }
                int netInputs = 0;
                if (firstLine != null && firstLine.startsWith("[NN]")) {
                    String header = in.readLine();
                    String[] headerParts = header.split(",");
                    netInputs = Integer.parseInt(headerParts[0]);
                    hiddens = Integer.parseInt(headerParts[1]);
                    outputs = Integer.parseInt(headerParts[2]);
                    hiddenWeights = new double[netInputs][hiddens];
                    hiddenBias = new double[hiddens];
                    outputWeights = new double[hiddens][outputs];
                    outputBias = new double[outputs];

                    for (int i = 0; i < netInputs; i++) {
                        String[] dataParts = in.readLine().split(",");
                        for (int h = 0; h < hiddens; h++) {
                            hiddenWeights[i][h] = Double.parseDouble(dataParts[h]);
                        }
                    }
                    for (int h = 0; h < hiddens; h++) {
                        hiddenBias[h] = Double.parseDouble(in.readLine());
                    }
                    for (int h = 0; h < hiddens; h++) {
                        String[] dataParts = in.readLine().split(",");
                        for (int o = 0; o < outputs; o++) {
                            outputWeights[h][o] = Double.parseDouble(dataParts[o]);
                        }
                    }
                    for (int o = 0; o < outputs; o++) {
                        outputBias[o] = Double.parseDouble(in.readLine());
                    }
                } else {
                    break;
                }

                System.out.println("loading network #" + n);
                NeuralNetwork newNet;
                newNet = new NeuralNetwork(netInputs, hiddens, outputs, hiddenWeights, outputWeights, hiddenBias, outputBias);
                net.add(newNet);
            }
            System.out.println();
            System.out.println("Done reading file '" + filename + "'");
            System.out.println("Continue training...\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        return net;
    }

    // Function that prints the current settings and progress
    private static void printSettings() {
        System.out.println();
        System.out.println("## Configuration ##");
        System.out.println("enableActionInput \t\t= " + Globals.enableActionInput);
        System.out.println("enablePillInput \t\t= " + Globals.enablePillInput);
        System.out.println("enableGhostDistanceInput \t= " + Globals.enableGhostDistanceInput);
        System.out.println("enableGhostDirectionInput \t= " + Globals.enableGhostDirectionInput);
        System.out.println("enableGhostAfraidInput \t= " + Globals.enableGhostAfraidInput);
        System.out.println("enableEntrapmentInput \t\t= " + Globals.enableEntrapmentInput);
        System.out.println("enablePowerPillInput \t\t= " + Globals.enablePowerPillInput);
        System.out.println();
        System.out.println("Average lifeTime \t\t= " + avgLifeTime);
        System.out.println("Number of games played \t\t= " + gamesdone);
        System.out.println("Number of successful runs \t= " + numSuccess);
        System.out.println();
    }

}